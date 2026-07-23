package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.ReconciliationEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for ReconciliationEntity.
 * @author MANFOUO Braun
 */
@Repository
public interface ReconciliationR2dbcRepository extends R2dbcRepository<ReconciliationEntity, UUID> {
    Flux<ReconciliationEntity> findByTenantId(UUID tenantId);
    Mono<ReconciliationEntity> findByTenantIdAndPeriodYearAndPeriodMonth(UUID tenantId, int periodYear, int periodMonth);
}
