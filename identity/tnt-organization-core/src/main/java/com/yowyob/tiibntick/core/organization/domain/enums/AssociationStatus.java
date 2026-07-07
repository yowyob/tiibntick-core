package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Status of a sub-deliverer's association with a FreelancerOrganization.
 *
 * @author MANFOUO Braun
 */
public enum AssociationStatus {

    /** Invitation sent by the OWNER — awaiting acceptance from the sub-deliverer. */
    PENDING_ACCEPTANCE,

    /** Association active — sub-deliverer can receive missions from the org. */
    ACTIVE,

    /** Temporarily suspended by the OWNER. */
    SUSPENDED,

    /** Association permanently ended (either party terminated it). */
    TERMINATED
}
