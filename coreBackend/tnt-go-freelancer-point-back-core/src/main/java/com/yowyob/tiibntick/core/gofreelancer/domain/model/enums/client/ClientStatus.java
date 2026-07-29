package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client;

/**
 * Defines the lifecycle status of a client account.
 *
 * @author TiiBnTick Team
 */
public enum ClientStatus {

    ACTIVE("ACTIVE"),
    SUSPENDED("SUSPENDED"),
    REVOKED("REVOKED");

    private final String value;

    ClientStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClientStatus fromValue(String value) {
        for (ClientStatus status : ClientStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown client status: " + value);
    }
}
