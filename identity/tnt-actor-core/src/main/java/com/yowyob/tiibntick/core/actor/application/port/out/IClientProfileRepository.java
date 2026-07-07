package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IClientProfileRepository {

    Mono<Boolean> existsByActorId(UUID tenantId, UUID actorId);

    Mono<ClientProfile> findByActorId(UUID tenantId, UUID actorId);

    Mono<ClientProfile> save(ClientProfile profile);
}
