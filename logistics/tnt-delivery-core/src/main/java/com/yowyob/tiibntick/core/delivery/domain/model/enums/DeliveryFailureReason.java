package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Why a hand-over attempt or a delivery failed (spec §1.1). The client-side facts reuse the {@code CLIENT_*} names of
 * {@code IncidentType} (one name per notion, §2.3); a failed attempt is a delivery operation, not an incident (QO-3).
 */
public enum DeliveryFailureReason {

    // ── attempt reasons (row D9; D10 when the maximum is reached) ─────────────────────────────
    CLIENT_ABSENT_AT_DELIVERY(true),
    CLIENT_PHONE_UNREACHABLE(true),
    /** Refusal by the recipient: no new attempt, direct return (row D10, decision QO-3). */
    CLIENT_REFUSED_RECEPTION(true),
    CLIENT_REFUSED_OTP_VALIDATION(true),
    CLIENT_WRONG_ADDRESS(true),
    CLIENT_INCOMPLETE_ADDRESS(true),
    CLIENT_BUILDING_ACCESS_BLOCKED(true),

    // ── final failure reasons ─────────────────────────────────────────────────────────────────
    /** The maximum number of attempts is reached (D10). */
    MAX_ATTEMPTS_REACHED(false),
    /** Not collected at the destination relay before the end of storage (D12). */
    NOT_COLLECTED_AT_RELAY(false),
    /** Incident resolved without outcome (D20). */
    INCIDENT_UNRESOLVED(false),
    /** The parcel was declared lost by a dispute (D21). */
    PARCEL_LOST(false),
    /** The parcel was declared damaged by a dispute (D21). */
    PARCEL_DAMAGED(false);

    private final boolean attemptReason;

    DeliveryFailureReason(boolean attemptReason) {
        this.attemptReason = attemptReason;
    }

    /** @return whether a courier may report this reason for a failed hand-over attempt */
    public boolean isAttemptReason() {
        return attemptReason;
    }

    /** @return whether this attempt reason ends the delivery at once (refusal by the recipient, [B.4]) */
    public boolean endsDeliveryAtOnce() {
        return this == CLIENT_REFUSED_RECEPTION;
    }
}
