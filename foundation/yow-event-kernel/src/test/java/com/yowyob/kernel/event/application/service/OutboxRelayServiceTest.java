package com.yowyob.kernel.event.application.service;

import com.yowyob.kernel.event.application.port.out.BrokerAvailabilityPort;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OutboxRelayService — automatic re-publication with exponential backoff, then DEAD")
class OutboxRelayServiceTest {

    private static final Duration GRACE = Duration.ofSeconds(60);
    private static final Duration CLAIM = Duration.ofMinutes(2);
    /** 4 attempts in total, 10s → 20s → 40s, capped at 30s. */
    private static final RetryPolicy POLICY = new RetryPolicy(4, 10_000, 2.0, 30_000);

    private final EventEnvelopeRepository repository = mock(EventEnvelopeRepository.class);
    private final DeadLetterRepository dlq = mock(DeadLetterRepository.class);
    private final KafkaPublisherPort kafka = mock(KafkaPublisherPort.class);
    private final BrokerAvailabilityPort broker = mock(BrokerAvailabilityPort.class);
    private final EventMetricsPort metrics = mock(EventMetricsPort.class);
    private final OutboxRelayService relay =
            new OutboxRelayService(repository, dlq, kafka, broker, metrics, POLICY, 50, GRACE, CLAIM);

    @BeforeEach
    void defaults() {
        when(broker.isAvailable()).thenReturn(Mono.just(true));
        when(repository.claimForRelay(any(), anyInt(), any())).thenReturn(Mono.just(true));
        when(repository.updateStatus(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Mono.just(1));
        when(repository.recordRelayFailure(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(Mono.just(1));
        when(dlq.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
    }

    private static DomainEventEnvelope persisted(EnvelopeStatus status, int retryCount, int version) {
        return DomainEventEnvelope.restore(EnvelopeId.generate(), UUID.randomUUID().toString(), null,
                "ProbeEvent", "agg-1", "Probe", "tenant-1", "TNT", "{\"eventId\":\"e1\"}", 1,
                "tnt.probe", "agg-1", status, retryCount, retryCount > 0 ? "previous error" : null,
                LocalDateTime.now().minusMinutes(5), null, version, null);
    }

    @Test
    @DisplayName("a FAILED envelope is claimed, re-published and marked PUBLISHED with the post-claim version")
    void failedEnvelopeIsRepublished() {
        DomainEventEnvelope e = persisted(EnvelopeStatus.FAILED, 1, 2);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(e));
        when(kafka.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(1).verifyComplete();

        verify(repository).claimForRelay(e.getId(), 2, CLAIM);
        // claim: 2 → 3, markPublished: 3 → 4 (the repository matches version - 1 = 3, the claimed row)
        verify(repository).updateStatus(eq(e.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), eq(1), eq(4));
        verify(metrics).recordRelayOutcome(any(), eq("PUBLISHED"));
    }

    @Test
    @DisplayName("a stale PENDING envelope (crash before the direct publish outcome) is taken over")
    void stalePendingIsTakenOver() {
        DomainEventEnvelope e = persisted(EnvelopeStatus.PENDING, 0, 1);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(e));
        when(kafka.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(1).verifyComplete();

        verify(kafka).publish(org.mockito.ArgumentMatchers.<DomainEventEnvelope>argThat(x -> x.getId().equals(e.getId())));
        verify(repository).updateStatus(eq(e.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), eq(0), eq(3));
    }

    @Test
    @DisplayName("a failed attempt reschedules the envelope FAILED with an exponential delay")
    void failureSchedulesBackoff() {
        DomainEventEnvelope second = persisted(EnvelopeStatus.FAILED, 1, 2);
        DomainEventEnvelope third = persisted(EnvelopeStatus.FAILED, 2, 5);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(second, third));
        when(kafka.publish(any())).thenReturn(Mono.error(new IllegalStateException("record too large")));

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(2).verifyComplete();

        // attempt 2 failed → next in 10s * 2^(2-1) = 20s ; attempt 3 failed → 40s capped at 30s
        verify(repository).recordRelayFailure(eq(second.getId()), eq(EnvelopeStatus.FAILED), eq("record too large"),
                eq(2), eq(4), eq(Duration.ofSeconds(20)));
        verify(repository).recordRelayFailure(eq(third.getId()), eq(EnvelopeStatus.FAILED), eq("record too large"),
                eq(3), eq(7), eq(Duration.ofSeconds(30)));
        verify(dlq, never()).save(any());
        verify(metrics, times(2)).recordRelayOutcome(any(), eq("RETRY_SCHEDULED"));
    }

    @Test
    @DisplayName("the last allowed attempt failing makes the envelope DEAD, with a DLQ entry and the DEAD metric")
    void maxAttemptsMakesItDead() {
        DomainEventEnvelope e = persisted(EnvelopeStatus.FAILED, 3, 6);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(e));
        when(kafka.publish(any())).thenReturn(Mono.error(new IllegalStateException("unknown topic")));

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(1).verifyComplete();

        verify(repository).recordRelayFailure(eq(e.getId()), eq(EnvelopeStatus.DEAD), eq("unknown topic"),
                eq(4), eq(8), eq(Duration.ZERO));
        ArgumentCaptor<DeadLetterEntry> entry = ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(dlq).save(entry.capture());
        assertThat(entry.getValue().getOriginalEnvelopeId()).isEqualTo(e.getId());
        assertThat(entry.getValue().getKafkaTopic()).isEqualTo("tnt.probe");
        verify(metrics).recordRelayOutcome(any(), eq("DEAD"));
        verify(metrics).recordMovedToDlq(any());
    }

    @Test
    @DisplayName("an envelope claimed by another instance in the meantime is left alone")
    void lostClaimIsSkipped() {
        DomainEventEnvelope e = persisted(EnvelopeStatus.FAILED, 1, 2);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(e));
        when(repository.claimForRelay(e.getId(), 2, CLAIM)).thenReturn(Mono.just(false));

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(0).verifyComplete();

        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("an unreachable broker skips the cycle without consuming any attempt")
    void brokerDownSkipsCycle() {
        when(broker.isAvailable()).thenReturn(Mono.just(false));

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(0).verifyComplete();

        verify(repository, never()).findRelayCandidates(any(), anyInt());
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("a send that times out stops the cycle: the next envelopes keep their attempts")
    void timeoutStopsTheCycle() {
        DomainEventEnvelope first = persisted(EnvelopeStatus.FAILED, 1, 2);
        DomainEventEnvelope second = persisted(EnvelopeStatus.FAILED, 1, 2);
        when(repository.findRelayCandidates(GRACE, 50)).thenReturn(Flux.just(first, second));
        when(kafka.publish(any())).thenReturn(Mono.error(
                new RuntimeException("send failed", new java.util.concurrent.TimeoutException("metadata"))));

        StepVerifier.create(relay.relayDueEnvelopes()).expectNext(1).verifyComplete();

        verify(kafka, times(1)).publish(any());
        verify(repository, never()).claimForRelay(eq(second.getId()), anyInt(), any());
    }

    @Test
    @DisplayName("claimForRelay refuses PUBLISHED and DEAD envelopes")
    void claimGuards() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> persisted(EnvelopeStatus.PUBLISHED, 0, 1).claimForRelay())
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> persisted(EnvelopeStatus.DEAD, 4, 1).claimForRelay())
                .isInstanceOf(IllegalStateException.class);
    }
}
