package com.yowyob.tiibntick.core.resource.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.domain.event.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Driven adapter: publishes domain events to Kafka topics using the reactive Kafka producer.
 *
 * <p>Topic naming convention: tnt.resource.{event-type}
 * <ul>
 *   <li>tnt.resource.vehicle.registered</li>
 *   <li>tnt.resource.vehicle.assigned</li>
 *   <li>tnt.resource.vehicle.location-updated</li>
 *   <li>tnt.resource.maintenance.alert-triggered</li>
 *   <li>tnt.resource.freelancer.vehicle.registered ()</li>
 *   <li>tnt.vehicle.assigned_to_mission ( — 6 partitions)</li>
 *   <li>tnt.vehicle.released_from_mission ( — 6 partitions)</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
public class ResourceKafkaEventPublisher implements ResourceEventPublisherPort {

    // ── Agency vehicle topics ─────────────────────────────────────────────
    private static final String TOPIC_VEHICLE_REGISTERED    = "tnt.resource.vehicle.registered";
    private static final String TOPIC_VEHICLE_ASSIGNED      = "tnt.resource.vehicle.assigned";
    private static final String TOPIC_VEHICLE_UNASSIGNED    = "tnt.resource.vehicle.unassigned";
    private static final String TOPIC_VEHICLE_MAINTENANCE   = "tnt.resource.vehicle.maintenance";
    private static final String TOPIC_VEHICLE_RETIRED       = "tnt.resource.vehicle.retired";
    private static final String TOPIC_VEHICLE_LOCATION      = "tnt.resource.vehicle.location";
    private static final String TOPIC_MAINTENANCE_ALERT     = "tnt.resource.maintenance.alert";
    private static final String TOPIC_EQUIPMENT_ASSIGNED    = "tnt.resource.equipment.assigned";

    // ── FreelancerVehicle topics () ──────────────────────────────────
    private static final String TOPIC_FL_VEHICLE_REGISTERED     = "tnt.resource.freelancer.vehicle.registered";
    private static final String TOPIC_FL_VEHICLE_ASSIGNED_MISSION = "tnt.vehicle.assigned_to_mission";
    private static final String TOPIC_FL_VEHICLE_RELEASED_MISSION = "tnt.vehicle.released_from_mission";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ResourceKafkaEventPublisher(
            @Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // ── Agency Vehicle events ─────────────────────────────────────────────

    @Override
    public Mono<Void> publish(VehicleRegisteredEvent event) {
        return sendEvent(TOPIC_VEHICLE_REGISTERED, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(VehicleAssignedEvent event) {
        return sendEvent(TOPIC_VEHICLE_ASSIGNED, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(VehicleUnassignedEvent event) {
        return sendEvent(TOPIC_VEHICLE_UNASSIGNED, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(VehicleSentToMaintenanceEvent event) {
        return sendEvent(TOPIC_VEHICLE_MAINTENANCE, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(VehicleRetiredEvent event) {
        return sendEvent(TOPIC_VEHICLE_RETIRED, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(VehicleLocationUpdatedEvent event) {
        return sendEvent(TOPIC_VEHICLE_LOCATION, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(MaintenanceAlertTriggeredEvent event) {
        return sendEvent(TOPIC_MAINTENANCE_ALERT, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(EquipmentAssignedEvent event) {
        return sendEvent(TOPIC_EQUIPMENT_ASSIGNED, event.equipmentId().toString(), event);
    }

    // ── FreelancerVehicle events () ──────────────────────────────────

    @Override
    public Mono<Void> publish(FreelancerVehicleRegisteredEvent event) {
        return sendEvent(TOPIC_FL_VEHICLE_REGISTERED, event.ownerOrgId().toString(), event);
    }

    @Override
    public Mono<Void> publish(FreelancerVehicleAssignedToMissionEvent event) {
        // Keyed by vehicleId for tnt-delivery-core and tnt-realtime-core ordering
        return sendEvent(TOPIC_FL_VEHICLE_ASSIGNED_MISSION, event.vehicleId().toString(), event);
    }

    @Override
    public Mono<Void> publish(FreelancerVehicleReleasedFromMissionEvent event) {
        return sendEvent(TOPIC_FL_VEHICLE_RELEASED_MISSION, event.vehicleId().toString(), event);
    }

    // ── Private helper ────────────────────────────────────────────────────

    private <T> Mono<Void> sendEvent(String topic, String key, T payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(topic, key, json)))
                .then()
                .onErrorResume(ex -> {
                    // Log and swallow — do not fail the domain operation for messaging failures
                    org.slf4j.LoggerFactory.getLogger(ResourceKafkaEventPublisher.class)
                            .error("Failed to publish event to topic={} key={}", topic, key, ex);
                    return Mono.empty();
                });
    }
}
