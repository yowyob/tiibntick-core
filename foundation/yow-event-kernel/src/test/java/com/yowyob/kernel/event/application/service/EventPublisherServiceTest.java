package com.yowyob.kernel.event.application.service;

import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("publish(): the envelope is stored, Kafka trouble marks it FAILED instead of failing the caller")
class EventPublisherServiceTest {

    private final EventEnvelopeRepository repository = mock(EventEnvelopeRepository.class);
    private final KafkaPublisherPort kafka = mock(KafkaPublisherPort.class);
    private final EventMetricsPort metrics = mock(EventMetricsPort.class);
    private final EventPublisherService service = new EventPublisherService(repository, kafka, metrics);

    private DomainEventEnvelope envelope() {
        return DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType("T").aggregateId("a").aggregateType("A").tenantId("t").solutionCode("TNT")
                .payload("{}").kafkaTopic("topic").build();
    }

    private void stubRepository() {
        when(repository.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(repository.updateStatus(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Mono.just(1));
    }

    @Test
    @DisplayName("a successful send marks the envelope PUBLISHED")
    void published() {
        stubRepository();
        DomainEventEnvelope e = envelope();
        when(kafka.publish(e)).thenReturn(Mono.empty());
        StepVerifier.create(service.publish(e)).verifyComplete();
        verify(repository).updateStatus(eq(e.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("an error signal from Kafka marks it FAILED and the caller is not failed")
    void failedSignal() {
        stubRepository();
        DomainEventEnvelope e = envelope();
        when(kafka.publish(e)).thenReturn(Mono.error(new IllegalStateException("broker down")));
        StepVerifier.create(service.publish(e)).verifyComplete();
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(repository).updateStatus(eq(e.getId()), eq(EnvelopeStatus.FAILED), isNull(), error.capture(), anyInt(), anyInt());
        org.assertj.core.api.Assertions.assertThat(error.getValue()).contains("broker down");
    }

    @Test
    @DisplayName("a port that THROWS (KafkaTemplate.send does when the broker is unreachable) is handled the same way")
    void failedThrow() {
        stubRepository();
        DomainEventEnvelope e = envelope();
        when(kafka.publish(e)).thenThrow(new RuntimeException("Send failed"));
        StepVerifier.create(service.publish(e)).verifyComplete();
        verify(repository).updateStatus(eq(e.getId()), eq(EnvelopeStatus.FAILED), isNull(), any(), anyInt(), anyInt());
        verify(metrics).recordFailed(eq(e), any());
    }

    @Test
    @DisplayName("publishAll() publishes and updates the envelopes AS PERSISTED (the insert owns the lock version)")
    void batchUsesPersistedEnvelopes() {
        DomainEventEnvelope in1 = envelope();
        DomainEventEnvelope in2 = envelope();
        DomainEventEnvelope saved1 = envelope();
        DomainEventEnvelope saved2 = envelope();
        when(repository.save(in1)).thenReturn(Mono.just(saved1));
        when(repository.save(in2)).thenReturn(Mono.just(saved2));
        when(repository.updateStatus(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Mono.just(1));
        when(kafka.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.publishAll(java.util.List.of(in1, in2))).expectNext(2).verifyComplete();

        verify(kafka).publish(saved1);
        verify(kafka).publish(saved2);
        verify(kafka, org.mockito.Mockito.never()).publish(in1);
        verify(repository).updateStatus(eq(saved1.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), anyInt(), anyInt());
        verify(repository).updateStatus(eq(saved2.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("publishAll(): one failing envelope is marked FAILED and the rest of the batch still goes out")
    void batchIsolatesFailures() {
        stubRepository();
        DomainEventEnvelope bad = envelope();
        DomainEventEnvelope good = envelope();
        when(kafka.publish(bad)).thenReturn(Mono.error(new IllegalStateException("boom")));
        when(kafka.publish(good)).thenReturn(Mono.empty());

        StepVerifier.create(service.publishAll(java.util.List.of(bad, good))).expectNext(2).verifyComplete();

        verify(repository).updateStatus(eq(bad.getId()), eq(EnvelopeStatus.FAILED), isNull(), any(), anyInt(), anyInt());
        verify(repository).updateStatus(eq(good.getId()), eq(EnvelopeStatus.PUBLISHED), any(), isNull(), anyInt(), anyInt());
    }
}
