package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/** Why a parcel must go back to its sender (spec §1.2 {@code returnCause}, §2.2; triggers of decision [B.4]). */
public enum ParcelReturnCause {
    /** The maximum number of hand-over attempts was reached (D10). */
    MAX_ATTEMPTS_REACHED,
    /** The recipient refused the parcel (D10). */
    CLIENT_REFUSED_RECEPTION,
    /** Not collected at the destination relay (D12). */
    NOT_COLLECTED_AT_RELAY,
    /** Incident resolved without outcome after pickup (D20). */
    INCIDENT_UNRESOLVED,
    /** The sender cancelled after pickup (D3). */
    SENDER_CANCELLED_AFTER_PICKUP,
    /** A dispute ordered the return ({@code RETURN_TO_SENDER_ORDERED}, P16). */
    DISPUTE_RETURN_TO_SENDER_ORDERED,
    /** The return delivery was cancelled (P19). */
    RETURN_DELIVERY_CANCELLED,
    /** The return delivery failed (P19, QO-8). */
    RETURN_DELIVERY_FAILED,
    /** The relay holding the parcel was revoked (D-129) and the parcel goes back to its sender. */
    RELAY_REVOKED
}
