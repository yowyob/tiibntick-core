package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyBranchEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyBranchR2dbcRepository extends ReactiveCrudRepository<AgencyBranchEntity, UUID> {

    Flux<AgencyBranchEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    Mono<AgencyBranchEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<Boolean> existsByCode(String code);
}
