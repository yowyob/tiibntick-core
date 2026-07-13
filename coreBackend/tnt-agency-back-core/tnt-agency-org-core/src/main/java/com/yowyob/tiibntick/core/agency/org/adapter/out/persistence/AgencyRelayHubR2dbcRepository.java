package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AgencyRelayHubR2dbcRepository extends ReactiveCrudRepository<AgencyRelayHubEntity, UUID> {

    Flux<AgencyRelayHubEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    Mono<AgencyRelayHubEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Mono<AgencyRelayHubEntity> findByCoreHubIdAndTenantId(UUID coreHubId, UUID tenantId);

    Mono<Boolean> existsByCode(String code);
}
