package com.yowyob.tiibntick.core.resource.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.domain.event.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link ResourceEventPublisherPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending to Kafka directly and swallowing send failures. Envelopes are persisted
 * in the same DB transaction as the business write (see the {@code @Transactional}
 * boundaries on {@code VehicleApplicationService}, {@code EquipmentApplicationService},
 * and {@code FreelancerFleetApplicationService}'s save-then-publish use cases), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ — a
 * business save can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the payload is still simply
 * {@code objectMapper.writeValueAsString(event)} — the raw event record serialized
 * directly, with no extra wrapper envelope — and the message keys are preserved
 * exactly as before via an explicit {@code kafkaPartitionKey}, so existing consumers
 * (tnt-realtime-core, tnt-notify-core, tnt-delivery-core, tnt-billing-pricing)
 * require no change.
 *
 * <p><b>FreelancerVehicle events have no {@code tenantId}</b> in their domain model —
 * {@code FreelancerVehicle} is scoped only by {@code ownerOrgId} (see the domain event
 * Javadoc). Since {@link DomainEventEnvelope} requires a non-null tenant id for routing,
 * {@code ownerOrgId} is reused as the envelope's tenant id for these three event types —
 * the closest available partition key, consistent with how the rest of the module already
 * treats {@code ownerOrgId} as the FreelancerOrg's tenancy boundary.
 *
 * @author MANFOUO Braun
 */
@Component
public class ResourceKafkaEventPublisher implements ResourceEventPublisherPort {

    // ── Agency vehicle topics ─────────────────────────────────────────────
    static final String TOPIC_VEHICLE_REGISTERED    = "tnt.resource.vehicle.registered";
    static final String TOPIC_VEHICLE_ASSIGNED      = "tnt.resource.vehicle.assigned";
    static final String TOPIC_VEHICLE_UNASSIGNED    = "tnt.resource.vehicle.unassigned";
    static final String TOPIC_VEHICLE_MAINTENANCE   = "tnt.resource.vehicle.maintenance";
    static final String TOPIC_VEHICLE_RETIRED       = "tnt.resource.vehicle.retired";
    static final String TOPIC_VEHICLE_LOCATION      = "tnt.resource.vehicle.location";
    static final String TOPIC_MAINTENANCE_ALERT     = "tnt.resource.maintenance.alert";
    static final String TOPIC_EQUIPMENT_ASSIGNED    = "tnt.resource.equipment.assigned";

    // ── FreelancerVehicle topics ──────────────────────────────────────────
    static final String TOPIC_FL_VEHICLE_REGISTERED     = "tnt.resource.freelancer.vehicle.registered";
    static final String TOPIC_FL_VEHICLE_ASSIGNED_MISSION = "tnt.vehicle.assigned_to_mission";
    static final String TOPIC_FL_VEHICLE_RELEASED_MISSION = "tnt.vehicle.released_from_mission";

    private static final String AGGREGATE_TYPE_VEHICLE = "Vehicle";
    private static final String AGGREGATE_TYPE_EQUIPMENT = "Equipment";
    private static final String AGGREGATE_TYPE_FREELANCER_VEHICLE = "FreelancerVehicle";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public ResourceKafkaEventPublisher(
            PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    // ── Agency Vehicle events ─────────────────────────────────────────────

    @Override
    public Mono<Void> publish(VehicleRegisteredEvent event) {
        return enqueue(TOPIC_VEHICLE_REGISTERED, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(VehicleAssignedEvent event) {
        return enqueue(TOPIC_VEHICLE_ASSIGNED, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(VehicleUnassignedEvent event) {
        return enqueue(TOPIC_VEHICLE_UNASSIGNED, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(VehicleSentToMaintenanceEvent event) {
        return enqueue(TOPIC_VEHICLE_MAINTENANCE, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(VehicleRetiredEvent event) {
        return enqueue(TOPIC_VEHICLE_RETIRED, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(VehicleLocationUpdatedEvent event) {
        return enqueue(TOPIC_VEHICLE_LOCATION, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(MaintenanceAlertTriggeredEvent event) {
        return enqueue(TOPIC_MAINTENANCE_ALERT, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_VEHICLE, event.vehicleId().toString(),
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(EquipmentAssignedEvent event) {
        return enqueue(TOPIC_EQUIPMENT_ASSIGNED, event, event.eventId(),
                event.equipmentId().toString(), AGGREGATE_TYPE_EQUIPMENT, event.equipmentId().toString(),
                event.tenantId(), event.occurredAt());
    }

    // ── FreelancerVehicle events ───────────────────────────────────────────

    @Override
    public Mono<Void> publish(FreelancerVehicleRegisteredEvent event) {
        return enqueue(TOPIC_FL_VEHICLE_REGISTERED, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_FREELANCER_VEHICLE,
                event.ownerOrgId().toString(), event.ownerOrgId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(FreelancerVehicleAssignedToMissionEvent event) {
        // Keyed by vehicleId for tnt-delivery-core and tnt-realtime-core ordering
        return enqueue(TOPIC_FL_VEHICLE_ASSIGNED_MISSION, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_FREELANCER_VEHICLE,
                event.vehicleId().toString(), event.ownerOrgId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publish(FreelancerVehicleReleasedFromMissionEvent event) {
        return enqueue(TOPIC_FL_VEHICLE_RELEASED_MISSION, event, event.eventId(),
                event.vehicleId().toString(), AGGREGATE_TYPE_FREELANCER_VEHICLE,
                event.vehicleId().toString(), event.ownerOrgId(), event.occurredAt());
    }

    // ── Private helper ────────────────────────────────────────────────────

    private Mono<Void> enqueue(String topic, Object event, UUID eventId, String aggregateId,
                               String aggregateType, String partitionKey, UUID tenantId,
                               Instant occurredAt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(eventId.toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .kafkaPartitionKey(partitionKey)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
