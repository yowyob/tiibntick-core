package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgVerifiedEvent;
import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;
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
 * {@link FreelancerOrgEventPublisherAdapter}.
 *
 * <p>Before this migration, the adapter published Spring {@code ApplicationEvent}s on the
 * (false) assumption that a {@code tnt-bootstrap} listener forwarded them to Kafka — no such
 * listener existed anywhere in the repo, so every {@code tnt.freelancer_org.*} event was
 * silently lost. This test proves the event is now durably enqueued via
 * {@link PublishEventUseCase} instead.
 */
class FreelancerOrgEventPublisherAdapterTest {

    private final PublishEventUseCase publishEventUseCase = mock(PublishEventUseCase.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FreelancerOrgEventPublisherAdapter adapter =
            new FreelancerOrgEventPublisherAdapter(publishEventUseCase, objectMapper);

    @Test
    void publishFreelancerOrgCreated_enqueuesEnvelopeOnTheCreatedTopic() {
        UUID orgId = UUID.randomUUID();
        FreelancerOrgCreatedEvent event = FreelancerOrgCreatedEvent.of(
                orgId, "FRL-tenant-1", "Acme Deliveries", UUID.randomUUID());

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.publishFreelancerOrgCreated(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());

        DomainEventEnvelope envelope = captor.getValue();
        assertThat(envelope.getKafkaTopic()).isEqualTo("tnt.freelancer_org.created");
        assertThat(envelope.getAggregateId()).isEqualTo(orgId.toString());
        assertThat(envelope.getAggregateType()).isEqualTo("FreelancerOrganization");
        assertThat(envelope.getTenantId()).isEqualTo("FRL-tenant-1");
        assertThat(envelope.getEventType()).isEqualTo("FreelancerOrgCreatedEvent");
    }

    @Test
    void publishFreelancerOrgVerified_enqueuesEnvelopeOnTheVerifiedTopic() {
        UUID orgId = UUID.randomUUID();
        FreelancerOrgVerifiedEvent event = FreelancerOrgVerifiedEvent.of(
                orgId, "FRL-tenant-2", UUID.randomUUID(), KycLevel.FULL, UUID.randomUUID());

        when(publishEventUseCase.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.publishFreelancerOrgVerified(event)).verifyComplete();

        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(publishEventUseCase).publish(captor.capture());
        assertThat(captor.getValue().getKafkaTopic()).isEqualTo("tnt.freelancer_org.verified");
    }

    @Test
    void publish_propagatesOutboxFailure_ratherThanSilentlyDroppingTheEvent() {
        FreelancerOrgCreatedEvent event = FreelancerOrgCreatedEvent.of(
                UUID.randomUUID(), "FRL-tenant-3", "Acme", UUID.randomUUID());

        when(publishEventUseCase.publish(any()))
                .thenReturn(Mono.error(new RuntimeException("db unavailable")));

        StepVerifier.create(adapter.publishFreelancerOrgCreated(event))
                .expectErrorMessage("db unavailable")
                .verify();
    }
}
