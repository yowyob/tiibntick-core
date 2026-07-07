package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Lifecycle status of a single wallet transaction.
 *
 * @author MANFOUO Braun
 */
public enum TransactionStatus {
    /** Transaction initiated — awaiting external confirmation (e.g. MoMo USSD). */
    PENDING,
    /** Transaction confirmed by the payment provider. */
    CONFIRMED,
    /** Transaction failed at the provider level. */
    FAILED,
    /** Transaction has been reversed. */
    REFUNDED
}
