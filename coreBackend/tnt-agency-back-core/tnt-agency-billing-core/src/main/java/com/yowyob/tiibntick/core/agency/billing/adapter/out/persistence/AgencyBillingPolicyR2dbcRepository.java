package com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity.BillingPolicyEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyBillingPolicyR2dbcRepository extends ReactiveCrudRepository<BillingPolicyEntity, UUID> {

    Mono<BillingPolicyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<BillingPolicyEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    @Query("SELECT * FROM agency_commercial.billing_policies WHERE agency_id = :agencyId AND tenant_id = :tenantId AND status = 'ACTIVE' LIMIT 1")
    Mono<BillingPolicyEntity> findActiveByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
