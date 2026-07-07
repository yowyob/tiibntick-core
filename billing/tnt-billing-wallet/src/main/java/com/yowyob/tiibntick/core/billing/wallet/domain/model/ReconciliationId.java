package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.util.UUID;

/**
 * Strongly typed identifier for ReconciliationRecord entities.
 *
 * @author MANFOUO Braun
 */
public record ReconciliationId(UUID value) {

    public ReconciliationId {
        if (value == null) throw new IllegalArgumentException("ReconciliationId value must not be null");
    }

    public static ReconciliationId generate() {
        return new ReconciliationId(UUID.randomUUID());
    }

    public static ReconciliationId of(UUID uuid) {
        return new ReconciliationId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
