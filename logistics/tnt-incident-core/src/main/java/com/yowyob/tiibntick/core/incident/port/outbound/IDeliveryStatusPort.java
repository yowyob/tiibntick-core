package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
/**
 * Outbound port: pause and resume delivery missions via tnt-delivery-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IDeliveryStatusPort {

    /** Resume triggers of rows D14/D16 (spec §1.1), carried on the delivery event. */
    String TRIGGER_INCIDENT_NOT_BLOCKING = "INCIDENT_NOT_BLOCKING";
    String TRIGGER_INCIDENT_CANCELLED = "INCIDENT_CANCELLED";
    String TRIGGER_INCIDENT_RESOLVED = "INCIDENT_RESOLVED";

    /**
     * Row D13: the courier declared an incident — the delivery enters the transient {@code INCIDENT_REPORTED} state
     * (QO-1) and memorises its operational state.
     */
    Mono<Void> markIncidentReported(UUID deliveryId, UUID incidentId);

    /** Row D15: a blocking incident pauses the delivery ({@code PAUSED_BY_INCIDENT}). */
    Mono<Void> pauseDelivery(UUID deliveryId, UUID incidentId);

    /**
     * Rows D14/D16/D18: the delivery goes back to its memorised state; trigger {@code INCIDENT_RESOLVED}.
     * After a hand-over ({@code INCIDENT_HANDOVER}) the replacement courier is required (D18).
     */
    Mono<Void> resumeDelivery(UUID deliveryId, UUID newDriverId, UUID newVehicleId);

    /**
     * Same as {@link #resumeDelivery(UUID, UUID, UUID)} with the cause of the resume ({@code TRIGGER_*} constants;
     * ignored after a hand-over, where the trigger is {@code HANDOVER_COMPLETED}).
     */
    Mono<Void> resumeDelivery(UUID deliveryId, String trigger, UUID newDriverId, UUID newVehicleId);

    /**
     * Row D18 with the point where the parcel changed hands (QO-11 / D-153): the delivery measures the leg of the
     * courier who leaves, to pay the replacement at the prorata of the distance. A {@code null} coordinate means
     * unknown (the payment is then left to the administrator). Default: the resume without the point.
     */
    default Mono<Void> resumeAfterHandover(UUID deliveryId, UUID newDriverId, UUID newVehicleId,
                                           Double handoverLatitude, Double handoverLongitude) {
        return resumeDelivery(deliveryId, TRIGGER_INCIDENT_RESOLVED, newDriverId, newVehicleId);
    }

    /** Row D17: a replacement courier takes the parcel over ({@code INCIDENT_HANDOVER}). */
    Mono<Void> startHandover(UUID deliveryId);

    /** Row D19: the hand-over is aborted; back to {@code PAUSED_BY_INCIDENT}. */
    Mono<Void> abortHandover(UUID deliveryId);

    /**
     * Row D20: the incident is resolved without outcome — the delivery fails ({@code INCIDENT_UNRESOLVED}). The parcel
     * goes back to its sender if it was collected, otherwise it is free again (QO-5).
     *
     * @param responsibleParty name of a {@code ResponsibleParty} constant of delivery-core (SENDER, RECIPIENT, COURIER,
     *                         PLATFORM, RELAY, AGENCY, NONE), may be {@code null}
     */
    Mono<Void> failDelivery(UUID deliveryId, UUID incidentId, String responsibleParty);

    Mono<DeliverySnapshot> getDeliverySnapshot(UUID deliveryId);

    /**
     * Declaration guard (lot L3, [D.1][D.3]): whether {@code actorId} (the caller's actor, from the token) is the
     * courier assigned to the delivery, and the delivery is not terminal.
     */
    Mono<Boolean> isAssignedCourier(UUID deliveryId, UUID actorId);

    /**
     * Whether the paused delivery's courier holds the parcel (memorised state in PICKED_UP, IN_TRANSIT,
     * OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED): a replacement then needs a hand-over (I12/D17), otherwise it is
     * assigned directly (I16/D16).
     */
    Mono<Boolean> courierHoldsParcel(UUID deliveryId);

    record DeliverySnapshot(UUID deliveryId, UUID currentDriverId, UUID currentVehicleId,
                           UUID agencyId, String status, Instant slaDeadline,
                           java.util.List<UUID> parcelIds) {}
}
