package com.yowyob.kernel.event.application.service;

import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxPollerService}, in particular the fix for a real
 * regression found while validating the Chantier C · Audit n°3 · P5 pilot
 * migration (see {@code docs/audits/remediation/chantier-c-p5-inventory.md}):
 * {@code publishAndCommit()} used to call {@code commitSuccess(entry, envelope)}
 * as a plain (eager) Java method argument to {@code .then(...)}, so
 * {@code envelope.getStatus()}/{@code getVersion()} were read at Reactor
 * chain-assembly time — <em>before</em> the {@code doOnSuccess} callback had
 * mutated the envelope to PUBLISHED — rather than after. The optimistic-lock
 * {@code WHERE version = :version - 1} clause built from those stale values
 * never matched the real DB row, so the update silently affected zero rows:
 * every envelope stayed PENDING forever and {@code fetchPendingBatch} kept
 * re-selecting (and re-publishing to Kafka) the same entries on every poll
 * cycle — a duplicate-delivery flood in production. Fixed by deferring the
 * mutation + {@code commitSuccess} call inside a single {@code Mono.defer(...)}.
 */
class OutboxPollerServiceTest {

    private final EventEnvelopeRepository envelopeRepository = mock(EventEnvelopeRepository.class);
    private final OutboxEntryRepository outboxRepository = mock(OutboxEntryRepository.class);
    private final DeadLetterRepository dlqRepository = mock(DeadLetterRepository.class);
    private final KafkaPublisherPort kafkaPublisher = mock(KafkaPublisherPort.class);
    private final EventMetricsPort metrics = mock(EventMetricsPort.class);

    private final OutboxPollerService service = new OutboxPollerService(
            envelopeRepository, outboxRepository, dlqRepository, kafkaPublisher, metrics);

    @Test
    void poll_marksEnvelopePublishedWithPostMutationVersion_notStalePreMutationValue() {
        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                .correlationId("corr-1")
                .eventType("TestEvent")
                .aggregateId("agg-1")
                .aggregateType("TestAggregate")
                .tenantId("tenant-1")
                .solutionCode("TNT")
                .payload("{}")
                .kafkaTopic("tnt.test.topic")
                .build();
        assertThat(envelope.getVersion()).isZero();
        assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);

        OutboxEntry entry = OutboxEntry.forEnvelope(envelope, null);

        when(outboxRepository.fetchPendingBatch(anyInt())).thenReturn(Flux.just(entry));
        when(envelopeRepository.findById(envelope.getId(), null)).thenReturn(Mono.just(envelope));
        when(kafkaPublisher.publish(envelope)).thenReturn(Mono.empty());
        when(outboxRepository.updateStatus(any(), any(), any())).thenReturn(Mono.just(1L));
        when(envelopeRepository.updateStatus(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(1));

        // Regression guard for the .count() bug fixed alongside this: poll() must report the
        // real number of processed entries, not always 0 (flatMap over Mono<Void> never emits).
        StepVerifier.create(service.poll())
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<EnvelopeStatus> statusCaptor = ArgumentCaptor.forClass(EnvelopeStatus.class);
        ArgumentCaptor<Integer> versionCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(envelopeRepository).updateStatus(
                eq(envelope.getId()), statusCaptor.capture(), any(), isNull(), eq(0), versionCaptor.capture());

        assertThat(statusCaptor.getValue()).isEqualTo(EnvelopeStatus.PUBLISHED);
        assertThat(versionCaptor.getValue())
                .as("must be the version AFTER markPublished() incremented it (1), not the stale "
                        + "pre-publish value (0) captured before the async Kafka send completed — "
                        + "otherwise the optimistic-lock WHERE clause never matches")
                .isEqualTo(1);

        verify(outboxRepository).updateStatus(eq(entry.getId()), eq(OutboxStatus.PROCESSED), any());
    }

    @Test
    void poll_callsHandleMissingEnvelopeExactlyOnce_whenEnvelopeTrulyNotFound() {
        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                .correlationId("corr-2")
                .eventType("TestEvent")
                .aggregateId("agg-2")
                .aggregateType("TestAggregate")
                .tenantId("tenant-1")
                .solutionCode("TNT")
                .payload("{}")
                .kafkaTopic("tnt.test.topic")
                .build();
        OutboxEntry entry = OutboxEntry.forEnvelope(envelope, null);

        when(outboxRepository.fetchPendingBatch(anyInt())).thenReturn(Flux.just(entry));
        when(envelopeRepository.findById(envelope.getId(), null)).thenReturn(Mono.empty());
        when(outboxRepository.updateStatus(any(), any(), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.poll())
                .expectNext(1)
                .verifyComplete();

        // Exactly once — not zero (the missing-envelope path must still run) and not twice
        // (the false-positive "empty" bug this test guards against).
        verify(outboxRepository, times(1))
                .updateStatus(eq(entry.getId()), eq(OutboxStatus.PROCESSED), any());
        verifyNoInteractions(kafkaPublisher);
    }
}
