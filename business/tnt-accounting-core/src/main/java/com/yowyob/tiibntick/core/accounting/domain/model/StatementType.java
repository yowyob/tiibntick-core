package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * Types of financial statements that can be generated.
 * Follows OHADA System Normal financial reporting structure.
 * Author: MANFOUO Braun
 */
public enum StatementType {

    /** Bilan — Balance Sheet (assets vs liabilities + equity). */
    BALANCE_SHEET,

    /** Compte de résultat — Profit & Loss statement. */
    INCOME_STATEMENT,

    /** Balance générale — Trial balance of all accounts. */
    TRIAL_BALANCE,

    /** Tableau des flux de trésorerie — Cash flow statement. */
    CASH_FLOW_STATEMENT
}
