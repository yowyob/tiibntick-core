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
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * Kafka consumer for incident events published by {@code tnt-incident-core}.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code tnt.incident.closed} — increments {@code incident_history_count} for the
 *       primary driver (deliverer or freelancer) involved in the closed incident.</li>
 * </ul>
 *
 * <p>The event payload is expected to contain at least:
 * <pre>{@code
 * {
 *   "driverActorId": "<UUID>",
 *   "tenantId": "<UUID>",
 *   "platform": "GO | FREELANCER | POINT | AGENCY"
 * }
 * }</pre>
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

    private final IDelivererRepository delivererRepository;
    private final IFreelancerRepository freelancerRepository;
    private final ObjectMapper objectMapper;

    public IncidentEventConsumer(IDelivererRepository delivererRepository,
                                  IFreelancerRepository freelancerRepository) {
        this.delivererRepository = delivererRepository;
        this.freelancerRepository = freelancerRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles the {@code tnt.incident.closed} event.
     *
     * <p>Extracts the {@code driverActorId} and {@code tenantId} from the event payload
     * and atomically increments the driver's {@code incident_history_count}.
     * If the actor is not found (incident closed without a driver assignment), the event
     * is silently ignored.
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.INCIDENT_CLOSED,
            groupId = "${spring.kafka.consumer.group-id:tiibntick-core}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onIncidentClosed(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.incident.closed event: key={}", record.key());

        Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(payload -> {
                    UUID driverActorId = extractUuid(payload, "driverActorId");
                    UUID tenantId = extractUuid(payload, "tenantId");

                    if (driverActorId == null || tenantId == null) {
                        // No driver involved in this incident (e.g. RELAY_POINT incident)
                        log.debug("tnt.incident.closed: no driverActorId in payload, skipping count increment");
                        return Mono.empty();
                    }

                    String platform = payload.has("platform")
                            ? payload.get("platform").asText() : "UNKNOWN";

                    return incrementIncidentCount(driverActorId, tenantId, platform);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Failed to process tnt.incident.closed event: {}",
                                error.getMessage(), error)
                );
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
