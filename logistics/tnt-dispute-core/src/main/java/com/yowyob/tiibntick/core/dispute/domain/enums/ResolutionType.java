package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Outcome type of a mediator or arbitrator ruling (spec §1.4, 8 values).
 *
 * <p>The first four grant a compensation (row LT7, then LT9 once paid); the others close the dispute without
 * compensation (row LT8). Each one decides what happens to the parcel ({@link #parcelOutcome()}, decision [B.7]).
 *
 * @author MANFOUO Braun
 */
public enum ResolutionType {
    /** Compensation without loss nor damage of the parcel: the parcel goes back to its previous state. */
    COMPENSATION_GRANTED,
    /** Partial compensation without loss nor damage (e.g. a delay): the parcel goes back to its previous state. */
    PARTIAL_COMPENSATION,
    /** Total loss compensated: the parcel is LOST (lot L4). */
    TOTAL_LOSS_COMPENSATED,
    /** Damage compensated: the parcel is DAMAGED (lot L4). */
    DAMAGE_COMPENSATED,
    COMPLAINT_DISMISSED,
    MUTUAL_AGREEMENT,
    /** A new delivery of the same parcel. */
    REDELIVERY_ORDERED,
    /** The parcel goes back to its sender (QO-16: name kept). */
    RETURN_TO_SENDER_ORDERED;

    /** @return whether the ruling grants a compensation (LT7) rather than closing the dispute (LT8) */
    public boolean grantsCompensation() {
        return this == COMPENSATION_GRANTED || this == PARTIAL_COMPENSATION || this == TOTAL_LOSS_COMPENSATED
                || this == DAMAGE_COMPENSATED;
    }

    /**
     * Effect on the parcel (delivery-core {@code DisputeParcelOutcome} names): {@code RESTORE_PREVIOUS}, {@code LOST},
     * {@code DAMAGED}, {@code RETURN_TO_SENDER}, {@code REDELIVERY}.
     */
    public String parcelOutcome() {
        return switch (this) {
            case TOTAL_LOSS_COMPENSATED -> "LOST";
            case DAMAGE_COMPENSATED -> "DAMAGED";
            case RETURN_TO_SENDER_ORDERED -> "RETURN_TO_SENDER";
            case REDELIVERY_ORDERED -> "REDELIVERY";
            case COMPENSATION_GRANTED, PARTIAL_COMPENSATION, COMPLAINT_DISMISSED, MUTUAL_AGREEMENT -> "RESTORE_PREVIOUS";
        };
    }
}
