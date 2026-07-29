package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.subscription;

/**
 * Represents the status of a subscription.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
public enum SubscriptionStatus {
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    SUSPENDED("SUSPENDED"),
    PENDING("PENDING");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown subscription status: " + value);
    }
}
