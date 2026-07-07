package com.yowyob.tiibntick.core.actor.domain.model;

/**
 * Role of a {@link FreelancerProfile} actor within a FreelancerOrganization
 * (managed by {@code tnt-organization-core}).
 *
 * <p>A FreelancerOrganization has exactly one OWNER (the creator), who may
 * associate up to {@code MAX_SUB_DELIVERERS} (=5) sub-deliverers. Sub-deliverers
 * operate under the org's banner and billing policy but are legally separate actors.
 *
 * <p>If a freelancer is not associated with any FreelancerOrganization, their
 * {@link FreelancerProfile#freelancerOrgId()} is null and this field is irrelevant.
 *
 * @author MANFOUO Braun
 */
public enum FreelancerRole {

    /**
     * Owner of the FreelancerOrganization. Manages the fleet, billing policy,
     * and sub-deliverers. The OWNER is the primary legal entity for missions.
     */
    OWNER,

    /**
     * Sub-deliverer working under the banner of a FreelancerOrganization OWNER.
     * Receives missions delegated by the OWNER or directly assigned by the system.
     * Earns a commission fraction of each mission revenue (defined in AssociatedDelivererRef).
     */
    SUB_DELIVERER;

    /**
     * Parses a {@link FreelancerRole} from a string value (case-insensitive).
     *
     * @param value the string representation
     * @return the corresponding enum constant, or {@code null} if the value is null/blank
     */
    public static FreelancerRole from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
