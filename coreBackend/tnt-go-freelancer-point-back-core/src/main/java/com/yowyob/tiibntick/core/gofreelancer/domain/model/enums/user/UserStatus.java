package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user;

/**
 * Lifecycle status of a base user account.
 *
 * @author François-Charles ATANGA
 */
public enum UserStatus {

    ACTIVE("ACTIVE"),
    SUSPENDED("SUSPENDED"),
    REVOKED("REVOKED");

    private final String value;

    UserStatus(String value) { this.value = value; }

    public String getValue() { return value; }

    public static UserStatus fromValue(String value) {
        for (UserStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown UserStatus: " + value);
    }
}
