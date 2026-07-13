package com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence.entity.CommissionRecordEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CommissionRecordR2dbcRepository extends ReactiveCrudRepository<CommissionRecordEntity, UUID> {

    Mono<CommissionRecordEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<CommissionRecordEntity> findByDelivererIdAndTenantId(UUID delivererId, UUID tenantId);

    Flux<CommissionRecordEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
