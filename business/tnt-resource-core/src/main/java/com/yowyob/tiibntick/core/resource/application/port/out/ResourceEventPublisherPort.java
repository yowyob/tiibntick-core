package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.event.*;
import reactor.core.publisher.Mono;

/**
 * Outbound port: publishes domain events from tnt-resource-core to Kafka topics.
 * Consumed by tnt-realtime-core, tnt-notify-core, tnt-delivery-core.
 *
 * <p> — Added FreelancerVehicle events for tnt-delivery-core and tnt-realtime-core.
 *
 * @author MANFOUO Braun
 */
public interface ResourceEventPublisherPort {

    // ── Agency Vehicle events ─────────────────────────────────────────────

    Mono<Void> publish(VehicleRegisteredEvent event);

    Mono<Void> publish(VehicleAssignedEvent event);

    Mono<Void> publish(VehicleUnassignedEvent event);

    Mono<Void> publish(VehicleSentToMaintenanceEvent event);

    Mono<Void> publish(VehicleRetiredEvent event);

    Mono<Void> publish(VehicleLocationUpdatedEvent event);

    Mono<Void> publish(MaintenanceAlertTriggeredEvent event);

    Mono<Void> publish(EquipmentAssignedEvent event);

    // ── FreelancerVehicle events () ───────────────────────────────────

    /**
     * Published to: {@code tnt.resource.freelancer.vehicle.registered}
     * Consumed by: tnt-notify-core, tnt-billing-pricing
     */
    Mono<Void> publish(FreelancerVehicleRegisteredEvent event);

    /**
     * Published to: {@code tnt.vehicle.assigned_to_mission} (6 partitions)
     * Consumed by: tnt-delivery-core, tnt-realtime-core
     */
    Mono<Void> publish(FreelancerVehicleAssignedToMissionEvent event);

    /**
     * Published to: {@code tnt.vehicle.released_from_mission} (6 partitions)
     * Consumed by: tnt-delivery-core
     */
    Mono<Void> publish(FreelancerVehicleReleasedFromMissionEvent event);
}
