package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoProposalEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DaoProposalR2dbcRepository extends ReactiveCrudRepository<DaoProposalEntity, UUID> {

    Mono<DaoProposalEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<DaoProposalEntity> findByTenantIdAndZoneId(UUID tenantId, UUID zoneId);

    Flux<DaoProposalEntity> findByTenantIdAndZoneIdAndStatus(UUID tenantId, UUID zoneId, String status);
}
