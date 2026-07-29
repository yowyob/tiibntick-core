package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer;

/**
 * Defines the lifecycle status of a freelancer account.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum FreelancerStatus {

    PENDING("PENDING"),
    APPROVED("APPROVED"),
    SUSPENDED("SUSPENDED"),
    REJECTED("REJECTED"),
    REVOKED("REVOKED");

    private final String value;

    FreelancerStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the string representation stored in the database.
     *
     * @return freelancer status value
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a database value to a FreelancerStatus enum.
     *
     * @param value database value
     * @return matching FreelancerStatus
     * @throws IllegalArgumentException if value is invalid
     */
    public static FreelancerStatus fromValue(String value) {
        for (FreelancerStatus status : FreelancerStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown freelancer status: " + value);
    }
}