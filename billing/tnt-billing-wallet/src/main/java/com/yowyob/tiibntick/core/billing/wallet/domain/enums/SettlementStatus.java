package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

public enum SettlementStatus {
    /** Kernel transfer confirmed. */
    PAID,
    /** Cash collected by the beneficiary (no money moved through the Kernel). */
    CASH_COLLECTED,
    /** Business object completed but the payer has not paid yet. */
    UNPAID
}
