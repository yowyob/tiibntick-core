package com.yowyob.tiibntick.core.tp.domain.model.enums;

/**
 * TiiBnTick-specific roles for third parties in the logistics ecosystem.
 * A third party can have multiple roles (e.g., SENDER and RECIPIENT).
 *
 * <p> — Added FreelancerOrg-related client roles:
 * <ul>
 *   <li>{@link #FREELANCER_ORG_CLIENT} — client directly linked to a FreelancerOrg.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum TntThirdPartyRole {

    /** Individual or company that sends packages. */
    SENDER,

    /** Individual or company that receives packages. */
    RECIPIENT,

    /** Registered deliverer working with an agency. */
    DELIVERER,

    /** Client of an agency who can order deliveries on behalf of the agency. */
    AGENCY_CLIENT,

    /** Freelance deliverer not attached to a fixed agency. */
    FREELANCER,

    /** Relay point / hub operator (Point de dépôt/retrait). */
    RELAY_POINT_OPERATOR,

    // ── : FreelancerOrg roles ─────────────────────────────────────────

    /**
     * Client directly linked to a FreelancerOrganization.
     * Used when a third party regularly uses the same freelancer's services
     * and bypasses the agency marketplace.
     *
     * <p>A ThirdParty with this role has a {@code FREELANCER_ORG} entry
     * in their {@code providerLinks} map.
     *
     * <p>DSL context: used to drive loyalty discounts and recurring client bonuses
     * in FreelancerOrg billing policies.
     */
    FREELANCER_ORG_CLIENT
}
