package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Platform commission lifecycle. The Kernel cannot yet split a transfer, so the commission is
 * only tracked here as DUE until it is settled out-of-band (see docs/audits/remediation/kernel-wallet-migration.md, R2).
 */
public enum CommissionStatus {
    NONE, DUE, SETTLED, WAIVED
}
