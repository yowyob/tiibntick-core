package com.yowyob.tiibntick.core.actor.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity.ClientProfileEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ClientProfileSpringDataRepository
        extends ReactiveCrudRepository<ClientProfileEntity, UUID> {

    Mono<Boolean> existsByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<ClientProfileEntity> findByTenantIdAndActorId(UUID tenantId, UUID actorId);

    Mono<ClientProfileEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    /** The person's client profile in any tenant (the oldest one): a profile follows the account, not the space. */
    @Query("""
            SELECT * FROM tnt_actor.client_profiles
            WHERE actor_id = :actorId
            ORDER BY created_at
            LIMIT 1
            """)
    Mono<ClientProfileEntity> findFirstByActorId(UUID actorId);
}
