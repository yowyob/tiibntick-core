package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * State of the return guarantee of a delivery to a relay hub (decisions D-147, D-151, spec of the administration
 * platform §5.2.1): an extra amount, equal to the sum that pays the delivery to the relay (with its shares), frozen on
 * the sender's wallet at the creation of the delivery, released when the recipient collects the parcel, debited at
 * the end of the relay storage to pay the return. The relay never pays a return.
 *
 * <p>{@code null} on a delivery means "no guarantee concerned" (not a delivery to a relay hub, or not yet priced).
 */
public enum ReturnGuaranteeStatus {
    /** Frozen on the sender's wallet. */
    HELD,
    /** Released: the parcel was collected, or the delivery ended without needing the return. */
    RELEASED,
    /** Debited at the end of the relay storage: it pays the return. */
    CAPTURED,
    /**
     * Not handled for now (D-147): the sender does not pay by wallet (cash, organisation…), the payment method or the
     * amount is unknown, or the wallet freeze is not connected yet (lot L5). Listed to the administrator (§5.2.1).
     */
    NOT_HANDLED,
    /** The wallet operation was refused (insufficient balance, Kernel error): to be handled by the administrator. */
    FAILED
}
