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

    /**
     * Individual freelancer courier, <b>standalone</b> — not part of any
     * FreelancerOrganization. Maps to {@code ActorType.FREELANCER} in
     * {@code identity/tnt-actor-core} specifically (not {@code FREELANCER_OWNER}/
     * {@code FREELANCER_SUB} — those belong to a FreelancerOrg and should target
     * {@link #FREELANCER_ORG} instead, never this value). Also corresponds to
     * {@code ActorRole.FREELANCER_DRIVER} in {@code tnt-incident-core} for incidents
     * with no {@code responsibleOrgId}. See {@code ClaimantType}'s javadoc for the
     * mirrored claimant-side mapping — these vocabularies are deliberately kept
     * separate per module and correlated only by UUID/string identity.
     */
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
     * on the {@code Dispute} carries the org UUID — this identifies the OWNER
     * ({@code ActorType.FREELANCER_OWNER}) as the legally responsible party.
     *
     * <p>When the mission was actually executed by a DRIVER/sub-deliverer
     * ({@code ActorType.FREELANCER_SUB}) rather than the OWNER directly, that
     * individual is tracked separately via {@code Dispute.impliedSubDelivererId}
     * / {@code Dispute.subDelivererInvolved} — the respondent stays the org
     * ({@code respondentOrgId}), while the sub-deliverer is carried alongside it
     * for compensation-split routing, not as a second respondent.
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
