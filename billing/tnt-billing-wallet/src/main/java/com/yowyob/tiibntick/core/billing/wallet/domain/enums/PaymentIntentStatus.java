package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Lifecycle status of a PaymentIntent.
 *
 * @author MANFOUO Braun
 */
public enum PaymentIntentStatus {
    /** Intent created — USSD push sent or redirect URL issued. */
    PENDING,
    /** External provider confirmed the payment. */
    CONFIRMED,
    /** Provider returned a failure response. */
    FAILED,
    /** Intent cancelled before confirmation. */
    CANCELLED,
    /** Intent expired (TTL exceeded without confirmation). */
    EXPIRED,
    /** Payment has been refunded. */
    REFUNDED
}
