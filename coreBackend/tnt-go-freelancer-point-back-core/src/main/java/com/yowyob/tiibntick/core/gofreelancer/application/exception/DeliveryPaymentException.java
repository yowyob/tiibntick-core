package com.yowyob.tiibntick.core.gofreelancer.application.exception;

/** Business refusal of a delivery-payment step; {@link Reason} drives the HTTP status. */
public class DeliveryPaymentException extends RuntimeException {

    public enum Reason {
        /** Caller is not allowed to perform this step on this delivery → 403. */
        FORBIDDEN,
        /** Delivery unknown → 404. */
        NOT_FOUND,
        /** State does not allow it (already paid, cash delivery, no tarif, cancelled, no courier) → 409. */
        CONFLICT
    }

    private final Reason reason;

    public DeliveryPaymentException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
