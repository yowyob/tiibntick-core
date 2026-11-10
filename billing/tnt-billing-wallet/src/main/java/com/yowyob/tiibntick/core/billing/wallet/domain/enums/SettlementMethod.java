package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/** How a business object was settled. */
public enum SettlementMethod {
    /** Payer transferred the amount from their Kernel wallet to the beneficiary's. */
    WALLET_TRANSFER,
    /** Beneficiary collected physical cash; only the platform commission is tracked. */
    CASH,
    /** Paid through a payment provider (Mobile Money, card) outside the Kernel wallet; reference only. */
    EXTERNAL_PROVIDER
}
