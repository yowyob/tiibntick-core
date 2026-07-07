package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Role of the FreelancerOrg member executing a delivery mission.
 *
 * <p>A FreelancerOrganization (TiiBnTick Freelancer sub-platform) has two roles:
 * <ul>
 *   <li>{@link #OWNER} — the founder/manager of the FreelancerOrg, executing directly.</li>
 *   <li>{@link #SUB_DELIVERER} — a partner deliverer invited by the OWNER, executing on behalf.</li>
 * </ul>
 *
 * <p>This role is used in:
 * <ul>
 *   <li>{@code Delivery.assignedFreelancerRole} — tracks which role is executing.</li>
 *   <li>{@code MissionStatusChangedEvent.freelancerRole} — for tnt-incident-core SLA monitoring.</li>
 *   <li>{@code tnt-billing-wallet} commission split (OWNER gets org revenue, SUB_DELIVERER gets commission).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum FreelancerRole {

    /**
     * The FreelancerOrg founder/manager executing the mission directly.
     * All mission revenue goes to the org wallet.
     */
    OWNER,

    /**
     * A partner/affiliate deliverer invited by the OWNER to execute a mission.
     * Revenue is split: org wallet receives base, sub-deliverer receives commission.
     */
    SUB_DELIVERER
}
