package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import java.util.UUID;

/**
 * Strongly typed identifier for PaymentIntent entities.
 *
 * @author MANFOUO Braun
 */
public record PaymentIntentId(UUID value) {

    public PaymentIntentId {
        if (value == null) throw new IllegalArgumentException("PaymentIntentId value must not be null");
    }

    public static PaymentIntentId generate() {
        return new PaymentIntentId(UUID.randomUUID());
    }

    public static PaymentIntentId of(UUID uuid) {
        return new PaymentIntentId(uuid);
    }

    public static PaymentIntentId of(String uuid) {
        return new PaymentIntentId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
