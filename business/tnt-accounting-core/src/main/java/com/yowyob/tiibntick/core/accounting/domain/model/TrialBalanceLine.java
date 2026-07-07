package com.yowyob.tiibntick.core.accounting.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One line in a trial balance report.
 * Author: MANFOUO Braun
 */
public record TrialBalanceLine(
        UUID accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        AccountCategory category,
        BigDecimal debitBalance,
        BigDecimal creditBalance
) {

    public TrialBalanceLine {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(accountCode);
        Objects.requireNonNull(accountName);
        Objects.requireNonNull(accountType);
        Objects.requireNonNull(category);
        Objects.requireNonNull(debitBalance);
        Objects.requireNonNull(creditBalance);
    }

    public static TrialBalanceLine from(Account account) {
        BigDecimal balance = account.getBalance();
        BigDecimal debit = account.getType().isDebitNormal() && balance.signum() >= 0
                ? balance : BigDecimal.ZERO;
        BigDecimal credit = account.getType().isCreditNormal() && balance.signum() >= 0
                ? balance : BigDecimal.ZERO;
        return new TrialBalanceLine(account.getId(), account.getCode(), account.getName(),
                account.getType(), account.getCategory(), debit, credit);
    }

    public BigDecimal netBalance() {
        return debitBalance.subtract(creditBalance);
    }
}
