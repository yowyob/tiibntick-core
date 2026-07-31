package com.yowyob.tiibntick.core.incident.domain.enums;
/**
 * Role of a human or system actor involved in an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * <p><b>Freelancer vocabulary mapping</b> — this module has its own local
 * {@code FREELANCER_DRIVER} value rather than reusing {@code tnt-actor-core}'s
 * {@code ActorType} (no compile dependency exists between the two in that direction).
 * Unlike {@code ActorType} (which splits standalone/{@code OWNER}/{@code SUB}) or
 * {@code RespondentType} (which splits standalone {@code FREELANCER} vs.
 * {@code FREELANCER_ORG}), {@code FREELANCER_DRIVER} here is a single flat value for
 * "the person physically driving" regardless of whether they are a standalone
 * freelancer, a FreelancerOrg OWNER, or a FreelancerOrg SUB — that distinction lives
 * instead on the {@code Incident} aggregate's {@code responsibleOrgId}/
 * {@code responsibleOrgType} fields ({@code responsibleOrgId == null} → standalone
 * freelancer; {@code responsibleOrgId} set + {@code responsibleOrgType ==
 * "FREELANCER_ORG"} → a FreelancerOrg mission, where {@code reportedByActorId} is
 * whichever of OWNER/SUB actually reported, not necessarily the org owner). When
 * correlating with an actor profile, {@code FREELANCER_DRIVER} corresponds to
 * {@code ActorType.FREELANCER}/{@code FREELANCER_OWNER}/{@code FREELANCER_SUB} in
 * {@code identity/tnt-actor-core} (see that enum's javadoc for the full three-way
 * breakdown), and — for disputes escalated from an incident — to
 * {@code ClaimantType.FREELANCER} / {@code RespondentType.FREELANCER}/
 * {@code FREELANCER_ORG} in {@code tnt-dispute-core}. These vocabularies are
 * intentionally not unified into a shared type; correlation across modules is by
 * UUID/string identity only, never by enum equality.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public enum ActorRole {
    SYSTEM, GO_BIKER, FREELANCER_DRIVER, PERMANENT_DRIVER, AGENCY_DRIVER,
    AGENCY_MANAGER, BRANCH_MANAGER, RELAY_POINT_OPERATOR,
    DISPATCHER, SUPPORT_AGENT, ADMIN_TIIBNTICK, CLIENT, RECIPIENT
}
