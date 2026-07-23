package com.yowyob.tiibntick.core.tp.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.ClientProfileRegisteredEvent;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.ThirdPartyRatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the Chantier C · Audit n°3 · P5 migration of
 * {@link KafkaTntTpEventPublisher} to the yow-event-kernel transactional outbox.
 */
class KafkaTntTpEventPublisherTest {

    private final PublishEventUseCase publishEventUseCase = mock(PublishEventUseCase.class);
    private final PublishEventBatchUseCase publishEventBatchUseCase = mock(PublishEventBatchUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final KafkaTntTpEventPublisher publisher =
            new KafkaTntTpEventPublisher(publishEventUseCase, publishEventBatchUseCase, objectMapper);

    @Test
    void publish_routesClientProfileRegisteredToItsTopicAndTenant() {
        UUID thirdPartyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ClientProfileRegisteredEvent event = new ClientProfileRegisteredEvent(
                UUID.randomUUID(), tenantId, thirdPartyId, Set.of(), Instant.now());

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());

        DomainEventEnvelope envelope = captor.getValue();
        assertThat(envelope.getKafkaTopic()).isEqualTo("tnt.tp.client.profile.events");
        assertThat(envelope.getAggregateId()).isEqualTo(thirdPartyId.toString());
        assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(envelope.getAggregateType()).isEqualTo("ThirdParty");
    }

    @Test
    void publish_routesThirdPartyRatedToRatingTopicKeyedByRatedParty() {
        UUID ratedThirdPartyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ThirdPartyRatedEvent event = new ThirdPartyRatedEvent(
                UUID.randomUUID(), tenantId, ratedThirdPartyId, "mission-1", 5.0, 4.5, Instant.now());

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publish(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());
        assertThat(captor.getValue().getKafkaTopic()).isEqualTo("tnt.tp.rating.events");
        assertThat(captor.getValue().getAggregateId()).isEqualTo(ratedThirdPartyId.toString());
    }

    @Test
    void publishAll_batchesAllEnvelopesInASingleOutboxCall() {
        UUID tenantId = UUID.randomUUID();
        ClientProfileRegisteredEvent e1 = new ClientProfileRegisteredEvent(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), Set.of(), Instant.now());
        ClientProfileRegisteredEvent e2 = new ClientProfileRegisteredEvent(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), Set.of(), Instant.now());

        when(publishEventBatchUseCase.publishAll(anyList())).thenReturn(Mono.just(2));

        StepVerifier.create(publisher.publishAll(List.of(e1, e2))).verifyComplete();

        ArgumentCaptor<List<DomainEventEnvelope>> captor = ArgumentCaptor.forClass(List.class);
        verify(publishEventBatchUseCase).publishAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void publish_propagatesOutboxFailure_ratherThanSilentlySwallowingIt() {
        ClientProfileRegisteredEvent event = new ClientProfileRegisteredEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(), Instant.now());

        when(publishEventUseCase.publish(any()))
                .thenReturn(Mono.error(new RuntimeException("db unavailable")));

        StepVerifier.create(publisher.publish(event))
                .expectErrorMessage("db unavailable")
                .verify();
    }
}
