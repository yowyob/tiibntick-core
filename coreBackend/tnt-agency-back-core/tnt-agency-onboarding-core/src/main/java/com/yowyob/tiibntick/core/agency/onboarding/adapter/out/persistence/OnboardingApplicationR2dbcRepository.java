package com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence.entity.OnboardingApplicationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OnboardingApplicationR2dbcRepository
        extends ReactiveCrudRepository<OnboardingApplicationEntity, UUID> {

    Mono<OnboardingApplicationEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    Mono<OnboardingApplicationEntity> findByApplicantUserIdAndTenantId(
            UUID applicantUserId, UUID tenantId);

    @Query("""
            SELECT o.* FROM agency_onboarding.onboarding_applications o
            INNER JOIN agency_org.agencies a ON a.id = o.agency_id
            WHERE o.application_status = :status
              AND a.status = 'PENDING_VALIDATION'
              AND o.tenant_id = :tenantId
            ORDER BY o.created_at DESC
            """)
    Flux<OnboardingApplicationEntity> findPendingByTenantId(String status, UUID tenantId);
}
