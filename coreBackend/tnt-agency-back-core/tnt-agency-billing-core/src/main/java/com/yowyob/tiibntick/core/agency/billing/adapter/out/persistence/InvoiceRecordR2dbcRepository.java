package com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.billing.adapter.out.persistence.entity.InvoiceRecordEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InvoiceRecordR2dbcRepository extends ReactiveCrudRepository<InvoiceRecordEntity, UUID> {

    Mono<InvoiceRecordEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<InvoiceRecordEntity> findByMissionIdAndTenantId(UUID missionId, UUID tenantId);

    Flux<InvoiceRecordEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);
}
