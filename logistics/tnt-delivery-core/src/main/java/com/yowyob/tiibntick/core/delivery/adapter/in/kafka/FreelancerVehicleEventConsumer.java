package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer for FreelancerVehicle assignment/release events from {@code tnt-resource-core}.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code tnt.vehicle.assigned_to_mission} — a FreelancerVehicle was assigned to a mission.
 *       Calls {@code DeliveryLifecycleUseCase.recordFreelancerVehicleAssigned()} to update
 *       the delivery's selectedVehicleId and billing context.</li>
 *   <li>{@code tnt.vehicle.released_from_mission} — a FreelancerVehicle was released.
 *       Clears the selectedVehicleId on the delivery (informational — no state change).</li>
 * </ul>
 *
 * <p>Expected payload for {@code tnt.vehicle.assigned_to_mission}:
 * <pre>{@code
 * {
 *   "vehicleId":   "<UUID>",     // FreelancerVehicle UUID from tnt-resource-core
 *   "ownerOrgId":  "<UUID>",     // FreelancerOrg UUID
 *   "vehicleType": "MOTO",       // VehicleType enum name
 *   "missionId":   "<UUID>",     // = deliveryId in tnt-delivery-core
 *   "occurredAt":  "2026-05-26T10:00:00Z"
 * }
 * }</pre>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class FreelancerVehicleEventConsumer {

    private final DeliveryLifecycleUseCase deliveryLifecycleUseCase;
    private final ObjectMapper objectMapper;

    public FreelancerVehicleEventConsumer(DeliveryLifecycleUseCase deliveryLifecycleUseCase,
            @Qualifier("deliveryObjectMapper") ObjectMapper objectMapper) {
        this.deliveryLifecycleUseCase = deliveryLifecycleUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles FreelancerVehicle assignment to a mission.
     * Records vehicleId and equipment IDs on the Delivery aggregate.
     */
    @KafkaListener(
            topics = "tnt.vehicle.assigned_to_mission",
            groupId = "${spring.kafka.consumer.group-id:tnt-delivery-core}",
            containerFactory = "deliveryKafkaListenerContainerFactory"
    )
    public void handleVehicleAssignedToMission(ConsumerRecord<String, String> record) {
        log.info("Received tnt.vehicle.assigned_to_mission: key={}", record.key());

        Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(json -> {
                    UUID deliveryId = extractUUID(json, "missionId");
                    if (deliveryId == null) {
                        log.warn("tnt.vehicle.assigned_to_mission: missing missionId in payload");
                        return Mono.empty();
                    }
                    String vehicleId = extractString(json, "vehicleId");
                    List<String> equipmentIds = extractStringList(json, "activeEquipmentIds");

                    return deliveryLifecycleUseCase.recordFreelancerVehicleAssigned(
                            deliveryId, vehicleId, equipmentIds)
                            .doOnSuccess(d -> log.info("Recorded vehicle={} for delivery={}",
                                    vehicleId, deliveryId))
                            .doOnError(e -> log.warn("Failed to record vehicle={} for delivery={}: {}",
                                    vehicleId, deliveryId, e.getMessage()))
                            .onErrorResume(e -> Mono.empty());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Handles FreelancerVehicle release from mission.
     * Logs the release — no delivery state change needed (informational event).
     */
    @KafkaListener(
            topics = "tnt.vehicle.released_from_mission",
            groupId = "${spring.kafka.consumer.group-id:tnt-delivery-core}",
            containerFactory = "deliveryKafkaListenerContainerFactory"
    )
    public void handleVehicleReleasedFromMission(ConsumerRecord<String, String> record) {
        log.info("Received tnt.vehicle.released_from_mission: key={}", record.key());

        Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .doOnNext(json -> {
                    UUID deliveryId = extractUUID(json, "missionId");
                    String vehicleId = extractString(json, "vehicleId");
                    log.debug("Vehicle={} released from delivery={}", vehicleId, deliveryId);
                })
                .onErrorResume(e -> {
                    log.warn("Error processing tnt.vehicle.released_from_mission: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private UUID extractUUID(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        try { return UUID.fromString(node.asText()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String extractString(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private List<String> extractStringList(JsonNode json, String field) {
        JsonNode node = json.get(field);
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }
}
