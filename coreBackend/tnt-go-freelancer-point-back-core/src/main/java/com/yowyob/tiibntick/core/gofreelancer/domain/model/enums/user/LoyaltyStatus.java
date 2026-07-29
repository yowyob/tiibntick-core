package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user;

/**
 * Loyalty tier of a client.
 *
 * @author François-Charles ATANGA
 */
public enum LoyaltyStatus {

    BRONZE("BRONZE"),
    SILVER("SILVER"),
    GOLD("GOLD"),
    PLATINUM("PLATINUM");

    private final String value;

    LoyaltyStatus(String value) { this.value = value; }

    public String getValue() { return value; }

    public static LoyaltyStatus fromValue(String value) {
        for (LoyaltyStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown LoyaltyStatus: " + value);
    }
}
