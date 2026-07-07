package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IRelayOperatorRepository {

    Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfile> findById(UUID tenantId, UUID id);

    Mono<RelayOperatorProfile> findByActorId(UUID tenantId, UUID actorId);

    Mono<RelayOperatorProfile> findByHubId(UUID tenantId, UUID hubId);

    Flux<RelayOperatorProfile> findByTenantId(UUID tenantId);

    Mono<RelayOperatorProfile> save(RelayOperatorProfile profile);
}
