package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.*;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Inbound port governing the complete delivery lifecycle state machine.
 *
 * <p> — Added FreelancerOrg integration methods.
 *
 * @author MANFOUO Braun
 */
public interface DeliveryLifecycleUseCase {

    /** Confirms parcel pickup. Transitions: CREATED → PICKED_UP */
    Mono<Delivery> confirmPickup(ConfirmPickupCommand command);

    /** Starts transit. Transitions: PICKED_UP → IN_TRANSIT */
    Mono<Delivery> startTransit(StartTransitCommand command);

    /** Deposits at relay point. Transitions: IN_TRANSIT → AT_RELAY_POINT */
    Mono<Delivery> depositAtRelayPoint(DepositAtRelayPointCommand command);

    /** Resumes from relay point. Transitions: AT_RELAY_POINT → IN_TRANSIT */
    Mono<Delivery> resumeFromRelayPoint(ResumeFromRelayPointCommand command);

    /** Updates real-time GPS and refines ETA. No status change. */
    Mono<EtaEstimate> updateLocation(UpdateDeliveryLocationCommand command);

    /** Completes the delivery. Transitions: IN_TRANSIT → DELIVERED */
    Mono<Delivery> completeDelivery(CompleteDeliveryCommand command);

    /** Marks delivery as failed. Transitions: IN_TRANSIT | PICKED_UP → FAILED */
    Mono<Delivery> failDelivery(FailDeliveryCommand command);

    /** Cancels the delivery. Transitions: CREATED | PICKED_UP → CANCELLED */
    Mono<Delivery> cancelDelivery(CancelDeliveryCommand command);

    // ── : FreelancerOrg integration ────────────────────────────────────

    /**
     * Assigns a FreelancerOrganization as the executor of this delivery.
     *
     * <p>Called when a client selects a FreelancerOrg's announcement response.
     * Sets platform="FREELANCER", records the org UUID and role.
     *
     * @param command the assignment command
     * @return updated Delivery with FreelancerOrg context
     */
    Mono<Delivery> assignToFreelancerOrg(AssignFreelancerOrgCommand command);

    /**
     * Records the FreelancerVehicle and equipment selected for this mission.
     *
     * <p>Called upon receiving the {@code tnt.vehicle.assigned_to_mission} Kafka event
     * from tnt-resource-core, confirming which vehicle was assigned.
     *
     * @param deliveryId   the delivery UUID
     * @param vehicleId    the FreelancerVehicle UUID
     * @param equipmentIds the deployed FreelancerEquipment IDs
     * @return updated Delivery
     */
    Mono<Delivery> recordFreelancerVehicleAssigned(java.util.UUID deliveryId,
            String vehicleId, List<String> equipmentIds);
}
