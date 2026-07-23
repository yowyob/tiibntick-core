package com.yowyob.tiibntick.core.actor.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the Chantier C · Audit n°3 · P5 migration of
 * {@link KafkaActorEventPublisher} to the yow-event-kernel transactional outbox.
 */
class KafkaActorEventPublisherTest {

    private final PublishEventUseCase publishEventUseCase = mock(PublishEventUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final KafkaActorEventPublisher publisher =
            new KafkaActorEventPublisher(publishEventUseCase, objectMapper);

    @Test
    void publishActorStatusChanged_enqueuesEnvelopeWithCorrectRoutingAndPayload() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ActorStatusChangedEvent event = ActorStatusChangedEvent.of(
                actorId, tenantId, "ACTIVE", "SUSPENDED", "policy violation");

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publishActorStatusChanged(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());

        DomainEventEnvelope envelope = captor.getValue();
        assertThat(envelope.getKafkaTopic()).isEqualTo("tnt.actor.status.changed");
        assertThat(envelope.getAggregateId()).isEqualTo(actorId.toString());
        assertThat(envelope.getAggregateType()).isEqualTo("Actor");
        assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(envelope.getEventType()).isEqualTo("ActorStatusChangedEvent");
        assertThat(envelope.getPayload()).contains("\"newStatus\":\"SUSPENDED\"");
    }

    @Test
    void publishBadgeEarned_routesToBadgeTopic() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        BadgeEarnedEvent event = BadgeEarnedEvent.of(actorId, tenantId, "DELIVERER", "TOP_RATED", "Top Rated");

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(publisher.publishBadgeEarned(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());
        assertThat(captor.getValue().getKafkaTopic()).isEqualTo("tnt.actor.badge.earned");
    }

    @Test
    void publish_propagatesOutboxFailure_soCallerCanReactRatherThanLoseTheEvent() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ActorStatusChangedEvent event = ActorStatusChangedEvent.of(
                actorId, tenantId, "ACTIVE", "SUSPENDED", "reason");

        when(publishEventUseCase.publish(any()))
                .thenReturn(Mono.error(new RuntimeException("db unavailable")));

        StepVerifier.create(publisher.publishActorStatusChanged(event))
                .expectErrorMessage("db unavailable")
                .verify();
    }
}
