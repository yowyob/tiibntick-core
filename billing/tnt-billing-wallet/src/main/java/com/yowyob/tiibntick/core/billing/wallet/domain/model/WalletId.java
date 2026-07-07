package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.util.UUID;

/**
 * Strongly typed identifier for the Wallet aggregate root.
 *
 * @author MANFOUO Braun
 */
public record WalletId(UUID value) {

    public WalletId {
        if (value == null) throw new IllegalArgumentException("WalletId value must not be null");
    }

    public static WalletId generate() {
        return new WalletId(UUID.randomUUID());
    }

    public static WalletId of(UUID uuid) {
        return new WalletId(uuid);
    }

    public static WalletId of(String uuid) {
        return new WalletId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
