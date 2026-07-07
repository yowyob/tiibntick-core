package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Payment channel through which a transaction is processed.
 *
 * @author MANFOUO Braun
 */
public enum PaymentChannel {
    /** MTN Mobile Money — Collections API v2 (Cameroon, West Africa). */
    MTN_MOMO,
    /** Orange Money — Orange Cameroon Payment API. */
    ORANGE_MONEY,
    /** Stripe — international card payment (PCI-DSS via Stripe). */
    STRIPE,
    /** Cash on delivery — manual receipt recorded by the agent. */
    CASH_ON_DELIVERY,
    /** In-app wallet balance transfer. */
    WALLET
}
