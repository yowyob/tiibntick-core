package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * Lifecycle states of a JournalEntry.
 * Author: MANFOUO Braun
 */
public enum JournalStatus {

    /** Draft — entry created but not yet validated. Lines may still be added. */
    DRAFT,

    /** Validated — entry is balanced and ready to be posted. Read-only. */
    VALIDATED,

    /** Posted — entry has been definitively recorded in the ledger. Immutable. */
    POSTED,

    /** Reversed — entry has been reversed by a counter-entry. */
    REVERSED,

    /** Cancelled — entry was discarded before posting (only allowed in DRAFT). */
    CANCELLED
}
