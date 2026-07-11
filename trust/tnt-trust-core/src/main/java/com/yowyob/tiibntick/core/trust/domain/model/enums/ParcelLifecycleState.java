package com.yowyob.tiibntick.core.trust.domain.model.enums;

/**
 * State Machine for a TiiBnTick parcel lifecycle.
 *
 * <p>Each state corresponds to one or more blockchain trust events.
 * Transitions are governed by the sequence of PACKAGE_CUSTODY_TRANSFERRED events
 * and DELIVERY_PROOF_RECORDED events on the ledger.
 *
 * @author MANFOUO Braun
 */
public enum ParcelLifecycleState {
    /** Parcel registered in the system but not yet picked up. */
    CREATED,
    /** Assigned to a deliverer or mission. */
    ASSIGNED,
    /** Picked up from sender by deliverer. */
    PICKED_UP,
    /** In transit between pickup and destination. */
    IN_TRANSIT,
    /** Temporarily deposited at a relay hub. */
    AT_RELAY_HUB,
    /** Out for final delivery to recipient. */
    OUT_FOR_DELIVERY,
    /** Delivered and confirmed with DeliveryProof on blockchain. */
    DELIVERED,
    /** Delivery failed; requires action. */
    DELIVERY_FAILED,
    /** Under dispute resolution process. */
    UNDER_DISPUTE,
    /** Returned to sender. */
    RETURNED
}
