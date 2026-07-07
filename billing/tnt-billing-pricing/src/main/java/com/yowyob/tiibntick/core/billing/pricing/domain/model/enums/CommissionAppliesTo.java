package com.yowyob.tiibntick.core.billing.pricing.domain.model.enums;

/**
 * Defines which type of deliverer actor a commission rule applies to.
 *
 * <h3> additions — FreelancerOrganization support</h3>
 * <ul>
 *   <li>{@link #FREELANCER_OWNER} — applies to the OWNER of a FreelancerOrganization.
 *       The owner receives the full org commission less the sub-deliverer share.</li>
 *   <li>{@link #SUB_DELIVERER} — applies to a SUB_DELIVERER working under a FreelancerOrg owner.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum CommissionAppliesTo {
    /** Permanent deliverer attached to an agency. */
    PERMANENT,
    /** Independent freelancer (solo, no org). */
    FREELANCER,
    /** Owner of a FreelancerOrganization. . */
    FREELANCER_OWNER,
    /** Sub-deliverer working under a FreelancerOrg owner. . */
    SUB_DELIVERER,
    /** Applies to all actor types. */
    ALL
}
