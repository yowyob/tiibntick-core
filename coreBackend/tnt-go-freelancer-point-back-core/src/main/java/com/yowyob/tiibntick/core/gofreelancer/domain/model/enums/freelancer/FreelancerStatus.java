package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer;

import java.util.Set;
import java.util.EnumMap;
import java.util.Map;

/**
 * Defines the lifecycle status of a freelancer account.
 *
 * <p>Allowed transitions (lot 18):
 * <pre>
 *   PENDING   → APPROVED, REJECTED
 *   APPROVED  → SUSPENDED, REVOKED
 *   SUSPENDED → APPROVED, REVOKED
 *   REJECTED  → (terminal)
 *   REVOKED   → (terminal)
 * </pre>
 * Transition to the same state is idempotent (always allowed, no side effects).
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

    private static final Map<FreelancerStatus, Set<FreelancerStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(FreelancerStatus.class);
        ALLOWED.put(PENDING,   Set.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED,  Set.of(SUSPENDED, REVOKED));
        ALLOWED.put(SUSPENDED, Set.of(APPROVED, REVOKED));
        ALLOWED.put(REJECTED,  Set.of());
        ALLOWED.put(REVOKED,   Set.of());
    }

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
     * Returns true when transitioning from {@code current} to {@code target} is permitted.
     * Same-state transitions always return true (idempotent).
     */
    public static boolean isAllowed(FreelancerStatus current, FreelancerStatus target) {
        if (current == target) return true;
        return ALLOWED.getOrDefault(current, Set.of()).contains(target);
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