package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * Identifies the originating journal for a JournalEntry.
 * Follows OHADA journalization conventions.
 * Author: MANFOUO Braun
 */
public enum JournalType {

    /** Journal général (catch-all for miscellaneous entries). */
    GENERAL,

    /** Journal des ventes — generated from confirmed SalesOrders / invoices. */
    SALES,

    /** Journal des achats — generated from purchase orders. */
    PURCHASES,

    /** Journal de trésorerie — cash receipts and disbursements. */
    CASH,

    /** Journal de banque — bank transfers and mobile money. */
    BANK,

    /** Journal des opérations diverses — adjustments and corrections. */
    MISC,

    /** Journal des OD de paie — payroll entries. */
    PAYROLL,

    /** Journal des dotations aux amortissements — depreciation. */
    DEPRECIATION,

    /** Journal de clôture — end-of-period closing entries. */
    CLOSING,

    /** Journal commission livreur — courier commission entries. */
    COMMISSION
}
