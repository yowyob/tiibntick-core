package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.util.UUID;

/**
 * Strongly typed identifier for WalletTransaction entities.
 *
 * @author MANFOUO Braun
 */
public record TransactionId(UUID value) {

    public TransactionId {
        if (value == null) throw new IllegalArgumentException("TransactionId value must not be null");
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(UUID uuid) {
        return new TransactionId(uuid);
    }

    public static TransactionId of(String uuid) {
        return new TransactionId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
