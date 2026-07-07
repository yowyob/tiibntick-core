package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.accounting.adapter.out.persistence.entity.AccountingPeriodEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for AccountingPeriod entities.
 * Author: MANFOUO Braun
 */
public interface AccountingPeriodR2dbcRepository extends ReactiveCrudRepository<AccountingPeriodEntity, UUID> {

    @Query("SELECT * FROM accounting.accounting_periods WHERE tenant_id = :tenantId AND year = :year AND month = :month")
    Mono<AccountingPeriodEntity> findByTenantIdAndYearAndMonth(UUID tenantId, int year, int month);
}
