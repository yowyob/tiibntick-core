package com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.RelayOperatorProfileEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RelayOperatorSpringDataRepository
        extends ReactiveCrudRepository<RelayOperatorProfileEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfileEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfileEntity> findByTenantIdAndHubId(UUID tenantId, UUID hubId);

    Flux<RelayOperatorProfileEntity> findAllByTenantId(UUID tenantId);
}
