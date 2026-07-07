package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * Lifecycle status of an AccountingPeriod.
 * Author: MANFOUO Braun
 */
public enum PeriodStatus {

    /** Open — entries can be posted into this period. */
    OPEN,

    /** Closing — period is being closed; no new entries accepted. */
    CLOSING,

    /** Closed — period is definitively closed; entries are read-only. */
    CLOSED,

    /** Locked — period is locked by an auditor or regulator. */
    LOCKED
}
