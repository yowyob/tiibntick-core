package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyRegistryR2dbcRepository extends ReactiveCrudRepository<AgencyRegistryEntity, UUID> {

    Flux<AgencyRegistryEntity> findByTenantId(UUID tenantId);

    Mono<AgencyRegistryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<AgencyRegistryEntity> findByAgencyCode(String agencyCode);

    Mono<Boolean> existsByAgencyCode(String agencyCode);
}
