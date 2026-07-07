package com.yowyob.tiibntick.core.actor.domain.model;

/**
 * Type classification for TiiBnTick actor profiles.
 *
 * <p>Determines the actor's role within the platform and the type of
 * {@link TntActorProfile} subclass that holds their extended data.
 *
 * <h3> additions — FreelancerOrganization support</h3>
 * <ul>
 *   <li>{@link #FREELANCER_OWNER} — freelancer who is the OWNER of a
 *       FreelancerOrganization. Has full billing policy and fleet management access.</li>
 *   <li>{@link #FREELANCER_SUB} — freelancer acting as a sub-deliverer under a
 *       FreelancerOrganization OWNER. Has restricted access to org resources.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum ActorType {

    /** Permanent deliverer attached to an Agency and Branch. */
    PERMANENT_DELIVERER,

    /**
     * Independent freelancer not permanently attached to an agency.
     * May associate with agencies and/or join/create a FreelancerOrganization.
     */
    FREELANCER,

    /**
     * Owner of a FreelancerOrganization (sole proprietorship).
     * Manages a fleet of 1–3 vehicles and up to 5 sub-deliverers.
     * Introduced in .
     */
    FREELANCER_OWNER,

    /**
     * Sub-deliverer working under a FreelancerOrganization OWNER.
     * Receives missions delegated by the org. Commission-based revenue.
     * Introduced in .
     */
    FREELANCER_SUB,

    /** Operator of a Hub Relais (relay point). */
    RELAY_OPERATOR,

    /** Client (shipper or recipient of parcels). */
    CLIENT,

    /** Manager of an Agency. */
    AGENCY_MANAGER,

    /** Manager of an Agency Branch. */
    BRANCH_MANAGER;

    /**
     * Parses an {@link ActorType} from a string value (case-insensitive).
     *
     * @param value the string representation
     * @return the corresponding enum constant
     * @throws IllegalArgumentException if the value is null, blank, or unrecognized
     */
    public static ActorType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ActorType value must not be null or blank");
        }
        return valueOf(value.trim().toUpperCase());
    }
}
