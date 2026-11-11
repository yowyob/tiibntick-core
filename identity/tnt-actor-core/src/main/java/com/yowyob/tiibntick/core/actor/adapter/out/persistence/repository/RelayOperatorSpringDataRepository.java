package com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.RelayOperatorProfileEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RelayOperatorSpringDataRepository
        extends ReactiveCrudRepository<RelayOperatorProfileEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfileEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfileEntity> findByTenantIdAndHubId(UUID tenantId, UUID hubId);

    /** The person's relay-operator profile in any tenant (the oldest one): a profile follows the account. */
    @Query("""
            SELECT * FROM tnt_actor.relay_operator_profiles
            WHERE actor_id = :actorId
            ORDER BY created_at
            LIMIT 1
            """)
    Mono<RelayOperatorProfileEntity> findFirstByActorId(UUID actorId);

    Flux<RelayOperatorProfileEntity> findAllByTenantId(UUID tenantId);
}
