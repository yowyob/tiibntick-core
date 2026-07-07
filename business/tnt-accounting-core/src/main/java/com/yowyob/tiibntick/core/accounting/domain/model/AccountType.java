package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * Accounting account type following OHADA classification.
 * Determines the normal balance direction (debit or credit) for each account.
 * Author: MANFOUO Braun
 */
public enum AccountType {

    /** Asset accounts — normal balance is DEBIT (classes 1-5 subset). */
    ASSET,

    /** Liability accounts — normal balance is CREDIT. */
    LIABILITY,

    /** Equity / capital accounts — normal balance is CREDIT (class 1). */
    EQUITY,

    /** Revenue accounts — normal balance is CREDIT (class 7). */
    REVENUE,

    /** Expense accounts — normal balance is DEBIT (class 6). */
    EXPENSE,

    /** Contra-asset (depreciation, provisions) — normal balance is CREDIT. */
    CONTRA_ASSET,

    /** Contra-revenue (returns, allowances) — normal balance is DEBIT. */
    CONTRA_REVENUE;

    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE || this == CONTRA_REVENUE;
    }

    public boolean isCreditNormal() {
        return !isDebitNormal();
    }
}
