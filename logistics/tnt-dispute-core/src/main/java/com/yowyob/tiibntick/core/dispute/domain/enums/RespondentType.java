package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Type of the party responding to (or targeted by) the dispute.
 *
 * <p> — Extended with {@link #FREELANCER_ORG}, {@link #HUB_POINT}, {@link #LINK_NETWORK}
 * to support the FreelancerOrganization model and TiiBnTick's multi-platform logistics.
 *
 * <p>Mapping to older values maintained for backward compatibility:
 * HUB_OPERATOR → use {@link #HUB_POINT}, NETWORK_OPERATOR → use {@link #LINK_NETWORK}.
 *
 * @author MANFOUO Braun
 */
public enum RespondentType {

    /** Individual freelancer courier (not part of a FreelancerOrg). */
    FREELANCER,

    /** Permanent deliverer employed by an Agency. */
    PERMANENT_DELIVERER,

    /** Hub relay point operator. Deprecated: use {@link #HUB_POINT}. */
    HUB_OPERATOR,

    /** Agency (main respondent for agency-dispatched deliveries). */
    AGENCY,

    /** Network operator (inter-city relay). Deprecated: use {@link #LINK_NETWORK}. */
    NETWORK_OPERATOR,

    /** The TiiBnTick platform itself (e.g. platform-related fraud or service failure). */
    PLATFORM,

    // ── : New respondent types ─────────────────────────────────────────

    /**
     * FreelancerOrganization — primary new respondent type ().
     *
     * <p>Used when the dispute targets a FreelancerOrg as a business entity
     * (rather than an individual freelancer). The {@code respondentOrgId} field
     * on the {@code Dispute} carries the org UUID.
     *
     * <p>Associated with: {@code Dispute.respondentOrgId} and optional
     * {@code Dispute.impliedSubDelivererId} when a SUB_DELIVERER is implicated.
     */
    FREELANCER_ORG,

    /**
     * Hub relay point (TiiBnTick Point sub-platform).
     * Replaces {@link #HUB_OPERATOR} for new dispute creation.
     */
    HUB_POINT,

    /**
     * TiiBnTick Link network operator (inter-city relay network).
     * Replaces {@link #NETWORK_OPERATOR} for new dispute creation.
     */
    LINK_NETWORK
}
