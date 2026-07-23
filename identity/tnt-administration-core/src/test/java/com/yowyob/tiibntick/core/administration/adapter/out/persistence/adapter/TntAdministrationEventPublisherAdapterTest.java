package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the Chantier C · Audit n°3 · P5 migration of
 * {@link TntAdministrationEventPublisherAdapter} to the yow-event-kernel transactional outbox.
 */
class TntAdministrationEventPublisherAdapterTest {

    private final PublishEventUseCase publishEventUseCase = mock(PublishEventUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TntAdministrationEventPublisherAdapter adapter =
            new TntAdministrationEventPublisherAdapter(publishEventUseCase, objectMapper);

    @Test
    void publish_enqueuesEnvelopeOnTheSingleAdministrationTopic_keyedByTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.publish(tenantId, "PlatformOptionsUpdated", "administration",
                        aggregateId, Map.of("field", "value")))
                .verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());

        DomainEventEnvelope envelope = captor.getValue();
        assertThat(envelope.getKafkaTopic()).isEqualTo("tnt.administration.events");
        assertThat(envelope.getAggregateId()).isEqualTo(aggregateId.toString());
        assertThat(envelope.getAggregateType()).isEqualTo("Administration");
        assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(envelope.getEventType()).isEqualTo("PlatformOptionsUpdated");
        // Pre-migration partitioning contract preserved: key = tenantId, not aggregateId.
        assertThat(envelope.getKafkaPartitionKey()).isEqualTo(tenantId.toString());
        assertThat(envelope.getPayload()).contains("\"module\":\"administration\"");
    }

    @Test
    void publish_propagatesOutboxFailure_ratherThanSilentlySwallowingIt() {
        when(publishEventUseCase.publish(any()))
                .thenReturn(Mono.error(new RuntimeException("db unavailable")));

        StepVerifier.create(adapter.publish(UUID.randomUUID(), "SomeEvent", "administration",
                        UUID.randomUUID(), Map.of()))
                .expectErrorMessage("db unavailable")
                .verify();
    }
}
