package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Lifecycle status of a {@link com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization}.
 *
 * <p>Transitions:
 * <pre>
 *   REGISTRATION_PENDING → UNDER_REVIEW → VERIFIED → ACTIVE
 *                                      ↘ REJECTED
 *   ACTIVE → SUSPENDED → ACTIVE (appeal accepted)
 *   ACTIVE → BLACKLISTED (fraud, repeated violations)
 * </pre>
 *
 * @author MANFOUO Braun
 */
public enum FreelancerRegStatus {

    /** Initial state — freelancer has started registration but not yet submitted all documents. */
    REGISTRATION_PENDING,

    /** Documents submitted — under admin review. */
    UNDER_REVIEW,

    /** Admin has approved the registration — KYC validated. */
    VERIFIED,

    /** Account is operational and accepting missions. */
    ACTIVE,

    /** Temporarily suspended (non-compliance, investigation). Account can appeal. */
    SUSPENDED,

    /** Registration rejected — reasons communicated to the freelancer. */
    REJECTED,

    /** Permanently banned due to fraud or severe repeated violations. */
    BLACKLISTED
}
