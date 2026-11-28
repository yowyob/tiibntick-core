package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/** How a business object was settled. */
public enum SettlementMethod {
    /**
     * Payer transferred the amount from their Kernel wallet to the beneficiary's (receive-code transfer, before lot
     * L5). Kept to read the history; no longer written (spec §4.5).
     */
    WALLET_TRANSFER,
    /**
     * Lot L5: held in a Kernel escrow at selection and released to the courier (and the platform levy) by the Kernel;
     * the platform commission is taken at source, so it is SETTLED with the payment.
     */
    ESCROW,
    /** Beneficiary collected physical cash; only the platform commission is tracked. */
    CASH,
    /** Paid through a payment provider (Mobile Money, card) outside the Kernel wallet; reference only. */
    EXTERNAL_PROVIDER
}
