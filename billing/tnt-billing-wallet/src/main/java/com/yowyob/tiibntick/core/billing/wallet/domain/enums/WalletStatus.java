package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Wallet lifecycle status.
 *
 * @author MANFOUO Braun
 */
public enum WalletStatus {
    /** Wallet is operational and accepts all operations. */
    ACTIVE,
    /** Wallet is temporarily frozen — no debit or payment allowed. */
    FROZEN,
    /** Wallet is permanently closed. */
    CLOSED
}
