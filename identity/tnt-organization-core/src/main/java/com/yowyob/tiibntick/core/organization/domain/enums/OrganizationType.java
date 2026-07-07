package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Classification of a TiiBnTick organization type.
 *
 * <p>Used to differentiate business rules, billing policies and permission scopes
 * between the various organization archetypes in the platform.
 *
 * @author MANFOUO Braun
 */
public enum OrganizationType {

    /** Traditional agency with branches, permanent staff, certified fleet. */
    AGENCY,

    /** Relay-point network operator (Link). */
    LINK_NETWORK,

    /**
     * Independent freelancer operating as a micro-organization (sole proprietorship).
     * Has its own fleet (1–3 vehicles), optional sub-deliverers (max 5), and
     * its own billing policy.
     */
    FREELANCER_ORG,

    /** Market-place operator. */
    MARKET_OPERATOR
}
