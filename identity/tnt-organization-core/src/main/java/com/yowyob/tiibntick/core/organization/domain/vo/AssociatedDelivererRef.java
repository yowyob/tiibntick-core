package com.yowyob.tiibntick.core.organization.domain.vo;

import com.yowyob.tiibntick.core.organization.domain.enums.AssociationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Value Object representing a sub-deliverer associated with a FreelancerOrganization.
 *
 * <p>A FreelancerOrganization OWNER can associate up to 5 sub-deliverers
 * (other TiiBnTick Freelancer actors) who work under the org's banner.
 * The OWNER remains legally responsible for missions delegated to sub-deliverers.
 *
 * @param delivererActorId  UUID of the sub-deliverer actor (from tnt-actor-core)
 * @param orgId             The FreelancerOrganization this association belongs to
 * @param status            Current association status
 * @param commissionRate    Fraction of mission revenue paid to the sub-deliverer (0.0–1.0)
 * @param associatedSince   Timestamp when the association became ACTIVE
 * @param terminatedAt      Timestamp when the association was terminated (nullable)
 *
 * @author MANFOUO Braun
 */
public record AssociatedDelivererRef(
        UUID delivererActorId,
        OrganizationId orgId,
        AssociationStatus status,
        BigDecimal commissionRate,
        Instant associatedSince,
        Instant terminatedAt
) {

    /**
     * Compact constructor — validates commission rate range.
     *
     * @throws IllegalArgumentException if commissionRate is outside [0, 1]
     */
    public AssociatedDelivererRef {
        if (commissionRate != null) {
            if (commissionRate.compareTo(BigDecimal.ZERO) < 0
                    || commissionRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "commissionRate must be in [0.0, 1.0], got: " + commissionRate);
            }
        }
    }

    /**
     * Factory — creates a pending invitation.
     *
     * @param delivererActorId UUID of the actor being invited
     * @param orgId            The FreelancerOrganization
     * @param commissionRate   Offered commission rate
     * @return a ref in {@link AssociationStatus#PENDING_ACCEPTANCE} status
     */
    public static AssociatedDelivererRef pending(UUID delivererActorId,
                                                  OrganizationId orgId,
                                                  BigDecimal commissionRate) {
        return new AssociatedDelivererRef(
                delivererActorId, orgId, AssociationStatus.PENDING_ACCEPTANCE,
                commissionRate, null, null);
    }

    /**
     * Returns a copy of this ref with {@link AssociationStatus#ACTIVE} and
     * the current timestamp as {@code associatedSince}.
     *
     * @return activated association ref
     */
    public AssociatedDelivererRef activate() {
        return new AssociatedDelivererRef(
                delivererActorId, orgId, AssociationStatus.ACTIVE,
                commissionRate, Instant.now(), null);
    }

    /**
     * Returns a copy of this ref with {@link AssociationStatus#TERMINATED} and
     * the current timestamp as {@code terminatedAt}.
     *
     * @return terminated association ref
     */
    public AssociatedDelivererRef terminate() {
        return new AssociatedDelivererRef(
                delivererActorId, orgId, AssociationStatus.TERMINATED,
                commissionRate, associatedSince, Instant.now());
    }

    /** @return {@code true} if this association is currently active */
    public boolean isActive() {
        return status == AssociationStatus.ACTIVE;
    }
}
