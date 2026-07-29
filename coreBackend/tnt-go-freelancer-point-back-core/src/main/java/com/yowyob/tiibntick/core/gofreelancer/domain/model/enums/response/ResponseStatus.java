package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.response;

/**
 * Defines the status of a courier response to an announcement.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum ResponseStatus {

    SENT("SENT"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED");

    private final String value;

    ResponseStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the database value.
     *
     * @return response status value
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string to a ResponseStatus enum.
     *
     * @param value database value
     * @return matching ResponseStatus
     * @throws IllegalArgumentException if value is invalid
     */
    public static ResponseStatus fromValue(String value) {
        for (ResponseStatus status : ResponseStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown response status: " + value);
    }
}