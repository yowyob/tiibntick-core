package com.yowyob.tiibntick.core.delivery.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import lombok.extern.slf4j.Slf4j;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
    private final ReactiveListenerSupport listenerSupport;

    public FreelancerVehicleEventConsumer(DeliveryLifecycleUseCase deliveryLifecycleUseCase,
            @Qualifier("deliveryObjectMapper") ObjectMapper objectMapper,
            ReactiveListenerSupport listenerSupport) {
        this.deliveryLifecycleUseCase = deliveryLifecycleUseCase;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport;
    }

    /**
     * Handles FreelancerVehicle assignment to a mission.
     * Records vehicleId and equipment IDs on the Delivery aggregate.
     */
    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.VEHICLE_ASSIGNED_TO_MISSION,
            groupId = "${tnt.kafka.consumer.groups.delivery-vehicle:tnt-delivery-vehicle-consumer}",
            containerFactory = "deliveryKafkaListenerContainerFactory"
    )
    public void handleVehicleAssignedToMission(ConsumerRecord<String, String> record) throws IOException {
        log.info("Received tnt.vehicle.assigned_to_mission: key={}", record.key());
        // Lot L7 (spec §3.10): awaited on the container thread; an unreadable record, a missing missionId, an
        // unknown delivery or a database error reaches the container: retries, then tnt.dlq.
        JsonNode json = objectMapper.readTree(record.value());
        UUID deliveryId = extractUUID(json, "missionId");
        if (deliveryId == null) {
            throw new IllegalArgumentException("tnt.vehicle.assigned_to_mission: missing or invalid missionId");
        }
        String vehicleId = extractString(json, "vehicleId");
        List<String> equipmentIds = extractStringList(json, "activeEquipmentIds");
        listenerSupport.await(deliveryLifecycleUseCase.recordFreelancerVehicleAssigned(
                deliveryId, vehicleId, equipmentIds));
        log.info("Recorded vehicle={} for delivery={}", vehicleId, deliveryId);
    }

    /**
     * Handles FreelancerVehicle release from mission.
     * Logs the release — no delivery state change needed (informational event). An unreadable record reaches the
     * container (retries, then tnt.dlq).
     */
    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.VEHICLE_RELEASED_FROM_MISSION,
            groupId = "${tnt.kafka.consumer.groups.delivery-vehicle:tnt-delivery-vehicle-consumer}",
            containerFactory = "deliveryKafkaListenerContainerFactory"
    )
    public void handleVehicleReleasedFromMission(ConsumerRecord<String, String> record) throws IOException {
        log.info("Received tnt.vehicle.released_from_mission: key={}", record.key());
        JsonNode json = objectMapper.readTree(record.value());
        log.debug("Vehicle={} released from delivery={}", extractString(json, "vehicleId"),
                extractUUID(json, "missionId"));
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
