package com.yowyob.tiibntick.core.billing.wallet.domain.enums;

/**
 * Status of a periodic wallet reconciliation record.
 *
 * @author MANFOUO Braun
 */
public enum ReconciliationStatus {
    /** Reconciliation scheduled but not yet executed. */
    PENDING,
    /** Reconciliation ran — no discrepancy found. */
    BALANCED,
    /** Reconciliation ran — a discrepancy was detected. */
    DISCREPANCY_FOUND,
    /** Discrepancy has been manually resolved. */
    RESOLVED
}
