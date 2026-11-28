package com.yowyob.tiibntick.core.actor.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Kafka consumer for incident events published by {@code tnt-incident-core}.
 *
 * <p>Listens to {@code IncidentClosed} of {@code tnt.incident.events} (lot L8, was {@code tnt.incident.closed}):
 * increments {@code incident_history_count} of the courier the incident is attributed to — the reporter when a
 * courier (or an automatic detector naming the driver, role SYSTEM) reported it, never the system actor. Other event
 * types and legacy records are ignored. The work is awaited (lot L7 rule): a failure is retried, then dead-lettered.
 *
 * <p>This consumer maintains the local {@code incident_history_count} counter in actor-core
 * without a direct dependency on tnt-incident-core's domain model. The counter is read back
 * by {@code ActorReputationPortAdapter.getIncidentHistoryCount()} when tnt-incident-core
 * queries the actor's incident history.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("actorIncidentEventConsumer")
public class IncidentEventConsumer {

    static final String CLOSED = "IncidentClosed";
    private static final java.util.Set<String> COURIER_ROLES = java.util.Set.of("GO_BIKER", "FREELANCER_DRIVER",
            "PERMANENT_DRIVER", "AGENCY_DRIVER", "SYSTEM");

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final ObjectMapper objectMapper;

    public IncidentEventConsumer(IDelivererRepository delivererRepository,
                                  IFreelancerRepository freelancerRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(
            topics = TntTopics.INCIDENT_EVENTS,
            groupId = "${tnt.kafka.consumer.groups.actor-incident:tnt-actor-incident-consumer}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onIncidentClosed(ConsumerRecord<String, String> record) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        if (!com.yowyob.tiibntick.common.kafka.TntAggregateEvent.isAggregateEvent(event)
                || !CLOSED.equals(event.path("eventType").asText())) {
            return;
        }
        UUID courier = courierInvolved(event.path("payload"));
        UUID tenantId = extractUuid(event, "tenantId");
        if (courier == null || tenantId == null) {
            log.debug("IncidentClosed {}: no courier involved, no count increment", event.path("aggregateId").asText());
            return;
        }
        String platform = event.path("payload").path("platform").asText("UNKNOWN");
        com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.awaitBounded(
                incrementIncidentCount(courier, tenantId, platform).then(Mono.just(true)),
                java.time.Duration.ofSeconds(30));
    }

    /** Same rule as incident-core's {@code IncidentEscalationService.courierInvolved}. */
    static UUID courierInvolved(JsonNode incident) {
        String role = incident.path("reportedByRole").asText(null);
        String reporter = incident.path("reportedByActorId").asText(null);
        if (role == null || reporter == null || !COURIER_ROLES.contains(role)
                || reporter.equals(String.valueOf(com.yowyob.tiibntick.common.tenant.SystemTenant.systemActorId()))) {
            return null;
        }
        try {
            return UUID.fromString(reporter);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Atomically increments the incident history count for the given actor.
     * Checks deliverer profiles first (most common case), then freelancer profiles.
     */
    private Mono<Void> incrementIncidentCount(UUID actorId, UUID tenantId, String platform) {
        if ("FREELANCER".equalsIgnoreCase(platform)) {
            return freelancerRepository.incrementIncidentHistoryCount(actorId, tenantId)
                    .doOnSuccess(v -> log.info(
                            "Incident history count incremented for freelancer {} in tenant {}",
                            actorId, tenantId));
        }
        // Default: try deliverer first, then fall back to freelancer
        return delivererRepository.incrementIncidentHistoryCount(actorId, tenantId)
                .switchIfEmpty(
                        freelancerRepository.incrementIncidentHistoryCount(actorId, tenantId))
                .doOnSuccess(v -> log.info(
                        "Incident history count incremented for actor {} in tenant {} (platform: {})",
                        actorId, tenantId, platform));
    }

    private UUID extractUuid(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.get(fieldName).asText());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for field '{}': {}", fieldName, node.get(fieldName).asText());
            return null;
        }
    }
}
