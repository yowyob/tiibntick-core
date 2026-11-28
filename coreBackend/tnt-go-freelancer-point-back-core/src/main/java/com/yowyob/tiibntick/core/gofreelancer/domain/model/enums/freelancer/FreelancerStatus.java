package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;

/**
 * API vocabulary of a freelancer's vetting status (PENDING, APPROVED, SUSPENDED, REJECTED, REVOKED).
 *
 * <p>Lot L3 de-duplication: the status is no longer stored by Go-Freelancer. It is read from and written to
 * the delivery-core {@link DeliveryPersonStatus} (same names), which owns the state machine. This enum only
 * remains as the type used by the existing routes and by callers outside the lot (to be replaced by
 * {@link DeliveryPersonStatus} when they are migrated).
 *
 * @author Kengfack Lagrange
 */
public enum FreelancerStatus {

    PENDING,
    APPROVED,
    SUSPENDED,
    REJECTED,
    REVOKED;

    /** Returns the string representation used by the API. */
    public String getValue() {
        return name();
    }

    /** The delivery-core status of the same name. */
    public DeliveryPersonStatus toCore() {
        return DeliveryPersonStatus.valueOf(name());
    }

    /** The API value of a delivery-core status ({@code null} stays {@code null}). */
    public static FreelancerStatus fromCore(DeliveryPersonStatus status) {
        return status == null ? null : valueOf(status.name());
    }

    /** Delegates to the delivery-core state machine. Same-state transitions are allowed (idempotent). */
    public static boolean isAllowed(FreelancerStatus current, FreelancerStatus target) {
        return current.toCore().canTransitionTo(target.toCore());
    }

    /** Converts an API value (case-insensitive) to a FreelancerStatus. */
    public static FreelancerStatus fromValue(String value) {
        for (FreelancerStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown freelancer status: " + value);
    }
}
