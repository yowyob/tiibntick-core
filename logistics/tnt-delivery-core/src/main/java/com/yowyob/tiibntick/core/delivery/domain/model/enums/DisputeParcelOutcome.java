package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Effect of a dispute outcome on the parcel (spec §1.4 "Issue → colis", decision [B.7], QO-16).
 * The mapping from the dispute resolution types is owned by dispute-core (lot L4).
 */
public enum DisputeParcelOutcome {
    /** Complaint dismissed, mutual agreement, compensation without loss/damage, withdrawal, expiry, closure (P13). */
    RESTORE_PREVIOUS,
    /** Total loss compensated (P14). */
    LOST,
    /** Damage compensated (P15). */
    DAMAGED,
    /** {@code RETURN_TO_SENDER_ORDERED} (P16). */
    RETURN_TO_SENDER,
    /** {@code REDELIVERY_ORDERED}: new delivery of the same parcel (P17). */
    REDELIVERY
}
