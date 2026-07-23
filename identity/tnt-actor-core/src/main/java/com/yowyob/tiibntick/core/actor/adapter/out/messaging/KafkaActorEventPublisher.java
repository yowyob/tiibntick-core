package com.yowyob.tiibntick.core.actor.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.domain.event.ActorLocationUpdatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.DelivererMissionAssignedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerAssociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerDissociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgLinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgUnlinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.KycValidatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed implementation of {@link IActorEventPublisher}.
 *
 * <p>Chantier C · Audit n°3 · P5 (identity domain): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of a
 * direct {@code KafkaTemplate.send()}. The envelope is persisted in the caller's
 * R2DBC transaction and relayed to Kafka asynchronously by {@code OutboxPollerService}
 * with retry/DLQ — the previous {@code onErrorResume(Mono.empty())} silent-loss
 * behaviour is gone. The Kafka message body remains the raw event JSON, so existing
 * consumers are unaffected.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaActorEventPublisher implements IActorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaActorEventPublisher.class);

    static final String TOPIC_ACTOR_STATUS_CHANGED      = "tnt.actor.status.changed";
    static final String TOPIC_ACTOR_LOCATION_UPDATED    = "tnt.actor.location.updated";
    static final String TOPIC_BADGE_EARNED              = "tnt.actor.badge.earned";
    static final String TOPIC_FREELANCER_ASSOCIATED     = "tnt.actor.freelancer.associated";
    static final String TOPIC_FREELANCER_DISSOCIATED    = "tnt.actor.freelancer.dissociated";
    static final String TOPIC_KYC_VALIDATED             = "tnt.actor.kyc.validated";
    static final String TOPIC_MISSION_ASSIGNED          = "tnt.actor.mission.assigned";
    /**  — published when a freelancer is linked to a FreelancerOrganization. */
    static final String TOPIC_FREELANCER_ORG_LINKED     = "tnt.actor.freelancer.org.linked";
    /**  — published when a freelancer's FreelancerOrganization link is removed. */
    static final String TOPIC_FREELANCER_ORG_UNLINKED   = "tnt.actor.freelancer.org.unlinked";

    private static final String AGGREGATE_TYPE = "Actor";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public KafkaActorEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntAuthObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishActorStatusChanged(ActorStatusChangedEvent event) {
        return enqueue(TOPIC_ACTOR_STATUS_CHANGED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishLocationUpdated(ActorLocationUpdatedEvent event) {
        return enqueue(TOPIC_ACTOR_LOCATION_UPDATED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishBadgeEarned(BadgeEarnedEvent event) {
        return enqueue(TOPIC_BADGE_EARNED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishFreelancerAssociated(FreelancerAssociatedEvent event) {
        return enqueue(TOPIC_FREELANCER_ASSOCIATED, event.eventId(), event.freelancerActorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishFreelancerDissociated(FreelancerDissociatedEvent event) {
        return enqueue(TOPIC_FREELANCER_DISSOCIATED, event.eventId(), event.freelancerActorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishKycValidated(KycValidatedEvent event) {
        return enqueue(TOPIC_KYC_VALIDATED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    @Override
    public Mono<Void> publishMissionAssigned(DelivererMissionAssignedEvent event) {
        return enqueue(TOPIC_MISSION_ASSIGNED, event.eventId(), event.delivererActorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    // FreelancerOrganization link events ─────────────────────────────

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgLinked(FreelancerOrgLinkedEvent event) {
        return enqueue(TOPIC_FREELANCER_ORG_LINKED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgUnlinked(FreelancerOrgUnlinkedEvent event) {
        return enqueue(TOPIC_FREELANCER_ORG_UNLINKED, event.eventId(), event.actorId(),
                event.tenantId(), event.occurredAt(), event);
    }

    // ── Private helper ─────────────────────────────────────────────────────────

    private Mono<Void> enqueue(String topic, UUID eventId, UUID aggregateId,
                               UUID tenantId, Instant occurredAt, Object payload) {
        return Mono.fromCallable(() -> serialize(payload))
                .map(json -> DomainEventEnvelope.wrap()
                        .correlationId(eventId.toString())
                        .eventType(payload.getClass().getSimpleName())
                        .aggregateId(aggregateId.toString())
                        .aggregateType(AGGREGATE_TYPE)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(json)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued {} to outbox for topic {}",
                        payload.getClass().getSimpleName(), topic))
                .doOnError(ex -> log.error(
                        "Failed to enqueue event to outbox for topic {}: {}", topic, ex.getMessage()));
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize event: " + obj.getClass().getSimpleName(), e);
        }
    }
}
