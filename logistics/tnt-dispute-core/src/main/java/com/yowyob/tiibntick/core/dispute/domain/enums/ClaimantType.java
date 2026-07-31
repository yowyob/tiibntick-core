package com.yowyob.tiibntick.core.dispute.domain.enums;

/**
 * Type of the party who filed the dispute.
 *
 * <p><b>Freelancer vocabulary mapping</b> — {@code FREELANCER} here is a single flat
 * value covering three distinct {@code identity/tnt-actor-core} concepts that this
 * enum does not itself distinguish: a standalone independent courier
 * ({@code ActorType.FREELANCER}), the OWNER of a FreelancerOrganization
 * ({@code ActorType.FREELANCER_OWNER}), or a SUB-deliverer/DRIVER working under one
 * ({@code ActorType.FREELANCER_SUB}). When the claimant is acting on behalf of, or as
 * a member of, a FreelancerOrganization, the claimant-side {@code claimantId} is still
 * just the individual actor UUID — the org itself never files a claim, only responds
 * to one (see {@code RespondentType.FREELANCER_ORG}). Also corresponds to
 * {@code ActorRole.FREELANCER_DRIVER} in {@code tnt-incident-core} when a dispute was
 * escalated from an incident. See {@code RespondentType}'s javadoc for the mirrored,
 * more detailed respondent-side mapping. These vocabularies are deliberately not
 * unified into a shared type — correlation across modules is by UUID/string identity
 * only, never by enum equality.
 *
 * @author MANFOUO Braun
 */
public enum ClaimantType {
    CLIENT,
    RECIPIENT,
    FREELANCER,
    PERMANENT_DELIVERER,
    HUB_OPERATOR,
    AGENCY_MANAGER,
    SYSTEM
}
