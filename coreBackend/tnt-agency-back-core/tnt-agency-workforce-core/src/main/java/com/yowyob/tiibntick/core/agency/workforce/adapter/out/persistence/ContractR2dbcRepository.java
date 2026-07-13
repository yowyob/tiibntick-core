package com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.ContractEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ContractR2dbcRepository extends ReactiveCrudRepository<ContractEntity, UUID> {

    @Query("SELECT * FROM agency_hr.contracts WHERE deliverer_id = :delivererId AND tenant_id = :tenantId AND status = 'ACTIVE' LIMIT 1")
    Mono<ContractEntity> findActiveByDelivererIdAndTenantId(UUID delivererId, UUID tenantId);

    Flux<ContractEntity> findByDelivererIdAndTenantId(UUID delivererId, UUID tenantId);

    Flux<ContractEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    Mono<ContractEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
