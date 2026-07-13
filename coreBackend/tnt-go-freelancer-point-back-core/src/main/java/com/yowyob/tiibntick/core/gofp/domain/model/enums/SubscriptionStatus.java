package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum SubscriptionStatus {
    ACTIVE,
    SUSPENDED,    // quota mensuel épuisé
    EXPIRED,
    CANCELLED;

    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown SubscriptionStatus: " + value);
    }
}
