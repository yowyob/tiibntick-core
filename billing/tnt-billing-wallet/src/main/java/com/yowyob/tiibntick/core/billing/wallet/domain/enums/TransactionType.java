package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Nature of a wallet movement.
 *
 * @author MANFOUO Braun
 */
public enum TransactionType {
    /** Funds added to the wallet (top-up, refund, commission credit). */
    CREDIT,
    /** Funds removed from the wallet (payment, withdrawal). */
    DEBIT,
    /** Amount reserved but not yet debited (pre-authorization). */
    RESERVE,
    /** Release of a previously reserved amount. */
    RELEASE,
    /** Platform commission credited to a deliverer wallet. */
    COMMISSION_CREDIT,
    /** Withdrawal of accumulated earnings to an external Mobile Money account. */
    WITHDRAWAL,
    /** Refund of a previously debited amount. */
    REFUND
}
