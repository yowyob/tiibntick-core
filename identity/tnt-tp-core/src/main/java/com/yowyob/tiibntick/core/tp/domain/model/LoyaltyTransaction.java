package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTransactionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Value Object: LoyaltyTransaction.
 * Records a single loyalty points movement within a LoyaltyAccount.
 *
 * @author MANFOUO Braun
 */
public record LoyaltyTransaction(
        UUID id,
        UUID loyaltyAccountId,
        int pointsDelta,
        LoyaltyTransactionType type,
        String externalRef,
        Instant occurredAt
) {
    public LoyaltyTransaction {
        if (id == null) throw new IllegalArgumentException("Transaction id is required");
        if (loyaltyAccountId == null) throw new IllegalArgumentException("loyaltyAccountId is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }

    /** Returns true if this transaction is a credit (positive delta). */
    public boolean isCredit() {
        return pointsDelta > 0;
    }

    /** Returns true if this transaction is a debit (negative delta). */
    public boolean isDebit() {
        return pointsDelta < 0;
    }
}
