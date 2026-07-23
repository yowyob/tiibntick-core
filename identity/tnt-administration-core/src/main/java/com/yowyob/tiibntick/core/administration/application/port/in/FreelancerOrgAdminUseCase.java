package com.yowyob.tiibntick.core.administration.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port for administrative operations on FreelancerOrganizations.
 *
 * <p>KYC/lifecycle operations are triggered by platform admins via
 * {@code TntAdministrationController} and forwarded to {@code FreelancerOrgAdminService}.
 *
 * <p>The service communicates with tnt-organization-core via Kafka events
 * (no direct service injection — cross-module boundary).
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOrgAdminUseCase {

    /**
     * Returns all FreelancerOrgs pending KYC basic verification.
     * Consumes tnt-organization-core data via the KYC event stream.
     *
     * @param tenantId the tenant scope
     * @return stream of org IDs awaiting verification
     */
    Flux<String> listPendingKycVerification(String tenantId);

    /**
     * Approves the basic KYC level for a FreelancerOrg.
     * Emits {@code FreelancerOrgKycApproved} event (topic: tnt.admin.freelancer_org.kyc_approved).
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID (from tnt-organization-core)
     * @param adminId  the approving admin actor UUID
     * @return Mono completing when the approval event is published
     */
    Mono<Void> approveKycBasic(String tenantId, String orgId, String adminId);

    /**
     * Approves the full KYC level for a FreelancerOrg (unlocks billing policy, fleet expansion).
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID
     * @param adminId  the approving admin actor UUID
     * @return Mono completing when the approval event is published
     */
    Mono<Void> approveKycFull(String tenantId, String orgId, String adminId);

    /**
     * Rejects the KYC verification for a FreelancerOrg with a reason.
     * Emits {@code FreelancerOrgKycRejected} event.
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID
     * @param adminId  the rejecting admin actor UUID
     * @param reason   the rejection reason for the FreelancerOrg owner
     * @return Mono completing when the rejection event is published
     */
    Mono<Void> rejectKyc(String tenantId, String orgId, String adminId, String reason);

    /**
     * Suspends a FreelancerOrg (blocks all missions and billing).
     * Emits {@code FreelancerOrgSuspended} event.
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID
     * @param adminId  the suspending admin actor UUID
     * @param reason   suspension reason
     * @return Mono completing when the suspension event is published
     */
    Mono<Void> suspendFreelancerOrg(String tenantId, String orgId, String adminId, String reason);

    /**
     * Unsuspends a previously suspended FreelancerOrg.
     * Emits {@code FreelancerOrgUnsuspended} event.
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID
     * @param adminId  the admin actor UUID
     * @return Mono completing when the event is published
     */
    Mono<Void> unsuspendFreelancerOrg(String tenantId, String orgId, String adminId);

    /**
     * Permanently blacklists a FreelancerOrg (irrecoverable — fraud, severe violation).
     * Emits {@code FreelancerOrgBlacklisted} event.
     *
     * @param tenantId the tenant scope (required by the outbox envelope)
     * @param orgId    FreelancerOrg UUID
     * @param adminId  the admin actor UUID
     * @param reason   blacklist reason
     * @return Mono completing when the event is published
     */
    Mono<Void> blacklistFreelancerOrg(String tenantId, String orgId, String adminId, String reason);
}
