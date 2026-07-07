package com.yowyob.tiibntick.core.administration.application.service;

import com.yowyob.tiibntick.core.administration.application.port.in.FreelancerOrgAdminUseCase;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Application service implementing administrative operations for FreelancerOrganizations.
 *
 * <p>All state transitions (KYC approval, suspension, blacklisting) are implemented as
 * event-driven operations: this service publishes Kafka events consumed by
 * {@code tnt-organization-core}, which owns the FreelancerOrg lifecycle.
 *
 * <p>This respects the cross-module boundary: tnt-administration-core does not inject
 * tnt-organization-core beans. It communicates via the event bus only.
 *
 * @author MANFOUO Braun
 */
@Service
public class FreelancerOrgAdminService implements FreelancerOrgAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgAdminService.class);

    /** Kafka topics for FreelancerOrg admin lifecycle events. */
    private static final String TOPIC_KYC_APPROVED   = "tnt.admin.freelancer_org.kyc_approved";
    private static final String TOPIC_KYC_REJECTED   = "tnt.admin.freelancer_org.kyc_rejected";
    private static final String TOPIC_SUSPENDED      = "tnt.admin.freelancer_org.suspended";
    private static final String TOPIC_UNSUSPENDED    = "tnt.admin.freelancer_org.unsuspended";
    private static final String TOPIC_BLACKLISTED    = "tnt.admin.freelancer_org.blacklisted";

    private final TntAdministrationEventPublisher eventPublisher;

    public FreelancerOrgAdminService(TntAdministrationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    @RequirePermission(resource = "administration", action = "govern:organizations")
    public Flux<String> listPendingKycVerification(String tenantId) {
        // FreelancerOrg listing is owned by tnt-organization-core.
        // Administration module acts as a coordinator, not a data owner.
        // In a real system this would query tnt-organization-core via its API or event replay.
        log.debug("Listing FreelancerOrgs pending KYC for tenant={}", tenantId);
        return Flux.empty(); // Delegated to tnt-organization-core query API
    }

    @Override
    @RequirePermission(resource = "freelancer", action = "approve")
    public Mono<Void> approveKycBasic(String orgId, String adminId) {
        log.info("Approving KYC BASIC for FreelancerOrg={} by admin={}", orgId, adminId);
        return eventPublisher.publish(TOPIC_KYC_APPROVED,
                buildEvent(orgId, adminId, "KYC_LEVEL", "BASIC", null));
    }

    @Override
    @RequirePermission(resource = "freelancer", action = "approve")
    public Mono<Void> approveKycFull(String orgId, String adminId) {
        log.info("Approving KYC FULL for FreelancerOrg={} by admin={}", orgId, adminId);
        return eventPublisher.publish(TOPIC_KYC_APPROVED,
                buildEvent(orgId, adminId, "KYC_LEVEL", "FULL", null));
    }

    @Override
    @RequirePermission(resource = "freelancer", action = "approve")
    public Mono<Void> rejectKyc(String orgId, String adminId, String reason) {
        log.info("Rejecting KYC for FreelancerOrg={} by admin={} reason={}", orgId, adminId, reason);
        return eventPublisher.publish(TOPIC_KYC_REJECTED,
                buildEvent(orgId, adminId, null, null, reason));
    }

    @Override
    @RequirePermission(resource = "freelancer", action = "write")
    public Mono<Void> suspendFreelancerOrg(String orgId, String adminId, String reason) {
        log.info("Suspending FreelancerOrg={} by admin={} reason={}", orgId, adminId, reason);
        return eventPublisher.publish(TOPIC_SUSPENDED,
                buildEvent(orgId, adminId, null, null, reason));
    }

    @Override
    @RequirePermission(resource = "freelancer", action = "write")
    public Mono<Void> unsuspendFreelancerOrg(String orgId, String adminId) {
        log.info("Unsuspending FreelancerOrg={} by admin={}", orgId, adminId);
        return eventPublisher.publish(TOPIC_UNSUSPENDED,
                buildEvent(orgId, adminId, null, null, null));
    }

    @Override
    @RequirePermission(resource = "tnt:platform", action = "admin")
    public Mono<Void> blacklistFreelancerOrg(String orgId, String adminId, String reason) {
        log.warn("BLACKLISTING FreelancerOrg={} by admin={} reason={}", orgId, adminId, reason);
        return eventPublisher.publish(TOPIC_BLACKLISTED,
                buildEvent(orgId, adminId, null, null, reason));
    }

    // ── private helper ────────────────────────────────────────────────────────

    private Map<String, Object> buildEvent(String orgId, String adminId,
                                             String paramKey, String paramValue, String reason) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("orgId", orgId);
        map.put("adminId", adminId);
        map.put("occurredAt", Instant.now().toString());
        if (paramKey != null) map.put(paramKey, paramValue);
        if (reason != null) map.put("reason", reason);
        return map;
    }
}
