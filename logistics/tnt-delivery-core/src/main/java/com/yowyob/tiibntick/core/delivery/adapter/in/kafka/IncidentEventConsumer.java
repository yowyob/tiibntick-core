package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * Kafka consumer for incident resolution events published by {@code tnt-incident-core}.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code tnt.incident.resolved} — incident resolved, delivery can be resumed.
 *       Calls {@code delivery.resumeFromIncident()} if the delivery was paused.</li>
 *   <li>{@code tnt.incident.closed} — incident definitively closed.
 *       Ensures any paused delivery is resumed (idempotent).</li>
 * </ul>
 *
 * <p>Expected event payload for both topics:
 * <pre>{@code
 * {
 *   "missionId": "<UUID>",      // = deliveryId in tnt-delivery-core
 *   "tenantId":  "<UUID>",
 *   "newDriverId": "<UUID>",    // optional — null if same driver resumes
 *   "newVehicleId": "<UUID>"    // optional — null if same vehicle
 * }
 * }</pre>
 *
 * <p>This consumer provides an alternative auto-resume path when the incident resolution
 * isn't driven through the {@code IMissionStatusPort.resumeMission()} direct call.
 * Both paths are idempotent — resuming an already-active delivery is a no-op.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("deliveryIncidentEventConsumer")
public class IncidentEventConsumer {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public IncidentEventConsumer(DeliveryRepository deliveryRepository,
                                  DeliveryEventPublisher eventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handles {@code tnt.incident.resolved} events.
     *
     * <p>If the resolved incident was blocking a delivery (missionId present),
     * resumes the delivery from incident pause.
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.INCIDENT_RESOLVED,
            groupId = "${spring.kafka.consumer.group-id:tiibntick-core}",
            containerFactory = "deliveryKafkaListenerContainerFactory")
    public void onIncidentResolved(ConsumerRecord<String, String> record) {
        log.info("Received tnt.incident.resolved — key={}", record.key());
        processIncidentResolutionEvent(record.value(), "tnt.incident.resolved");
    }

    /**
     * Handles {@code tnt.incident.closed} events.
     *
     * <p>Ensures any delivery paused by this incident is resumed.
     * Acts as a safety net for deliveries that weren't resumed at resolution time.
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.INCIDENT_CLOSED,
            groupId = "${spring.kafka.consumer.group-id:tiibntick-core}",
            containerFactory = "deliveryKafkaListenerContainerFactory")
    public void onIncidentClosed(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.incident.closed — key={}", record.key());
        // On closure, also resume any still-paused deliveries (idempotent fallback)
        processIncidentResolutionEvent(record.value(), "tnt.incident.closed");
    }

    // ── Private helpers ────────────────────────────────────────────────

    private void processIncidentResolutionEvent(String payload, String topic) {
        Mono.fromCallable(() -> objectMapper.readTree(payload))
                .flatMap(json -> {
                    UUID missionId   = extractUuid(json, "missionId");
                    UUID newDriverId  = extractUuid(json, "newDriverId");
                    UUID newVehicleId = extractUuid(json, "newVehicleId");
                    UUID incidentId   = extractUuid(json, "incidentId");

                    if (missionId == null) {
                        log.debug("{}: no missionId in payload — skipping delivery resume", topic);
                        return Mono.empty();
                    }

                    return deliveryRepository.findByIdNoTenant(missionId)
                            .flatMap(delivery -> {
                                if (!delivery.getStatus().isPausedByIncident()) {
                                    // Already resumed or not paused — idempotent no-op
                                    log.debug("{}: delivery {} not paused by incident (status={}) — skipping",
                                            topic, missionId, delivery.getStatus());
                                    return Mono.empty();
                                }
                                try {
                                    delivery.resumeFromIncident(newDriverId, newVehicleId);
                                    return deliveryRepository.save(delivery)
                                            .flatMap(saved -> eventPublisher.publishAll(
                                                    saved.getDomainEvents())
                                                    .doOnSuccess(v -> saved.clearDomainEvents())
                                                    .then());
                                } catch (Exception e) {
                                    log.warn("{}: could not resume delivery {} from incident {}: {}",
                                            topic, missionId, incidentId, e.getMessage());
                                    return Mono.empty();
                                }
                            })
                            .switchIfEmpty(Mono.fromRunnable(() ->
                                    log.debug("{}: delivery {} not found", topic, missionId)));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Failed to process {} event: {}",
                                topic, error.getMessage(), error)
                );
    }

    private UUID extractUuid(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        try {
            return UUID.fromString(node.get(field).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
