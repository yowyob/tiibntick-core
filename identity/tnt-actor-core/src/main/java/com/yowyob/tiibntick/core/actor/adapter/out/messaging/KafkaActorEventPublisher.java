package com.yowyob.tiibntick.core.actor.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka implementation of {@link IActorEventPublisher}.
 *
 * <p>All actor domain events are serialized as JSON and published to their
 * respective Kafka topics using the reactive producer template.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #TOPIC_FREELANCER_ORG_LINKED} — {@code tnt.actor.freelancer.org.linked}</li>
 *   <li>{@link #TOPIC_FREELANCER_ORG_UNLINKED} — {@code tnt.actor.freelancer.org.unlinked}</li>
 * </ul>
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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaActorEventPublisher(
            @Qualifier("actorKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntAuthObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishActorStatusChanged(ActorStatusChangedEvent event) {
        return send(TOPIC_ACTOR_STATUS_CHANGED, event.actorId().toString(), event);
    }

    @Override
    public Mono<Void> publishLocationUpdated(ActorLocationUpdatedEvent event) {
        return send(TOPIC_ACTOR_LOCATION_UPDATED, event.actorId().toString(), event);
    }

    @Override
    public Mono<Void> publishBadgeEarned(BadgeEarnedEvent event) {
        return send(TOPIC_BADGE_EARNED, event.actorId().toString(), event);
    }

    @Override
    public Mono<Void> publishFreelancerAssociated(FreelancerAssociatedEvent event) {
        return send(TOPIC_FREELANCER_ASSOCIATED, event.freelancerActorId().toString(), event);
    }

    @Override
    public Mono<Void> publishFreelancerDissociated(FreelancerDissociatedEvent event) {
        return send(TOPIC_FREELANCER_DISSOCIATED, event.freelancerActorId().toString(), event);
    }

    @Override
    public Mono<Void> publishKycValidated(KycValidatedEvent event) {
        return send(TOPIC_KYC_VALIDATED, event.actorId().toString(), event);
    }

    @Override
    public Mono<Void> publishMissionAssigned(DelivererMissionAssignedEvent event) {
        return send(TOPIC_MISSION_ASSIGNED, event.delivererActorId().toString(), event);
    }

    // FreelancerOrganization link events ─────────────────────────────

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgLinked(FreelancerOrgLinkedEvent event) {
        return send(TOPIC_FREELANCER_ORG_LINKED, event.actorId().toString(), event);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgUnlinked(FreelancerOrgUnlinkedEvent event) {
        return send(TOPIC_FREELANCER_ORG_UNLINKED, event.actorId().toString(), event);
    }

    // ── Private helper ─────────────────────────────────────────────────────────

    private Mono<Void> send(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> serialize(payload))
                .flatMap(json -> Mono.fromFuture(
                        kafkaTemplate.send(new ProducerRecord<>(topic, key, json))))
                .doOnError(ex -> log.error(
                        "Failed to publish event to topic {}: {}", topic, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
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
