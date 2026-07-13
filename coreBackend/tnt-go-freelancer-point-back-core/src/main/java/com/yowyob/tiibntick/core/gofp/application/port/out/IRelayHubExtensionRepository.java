package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubExtensionEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IRelayHubExtensionRepository {
    Mono<RelayHubExtensionEntity> save(RelayHubExtensionEntity entity);
    Mono<RelayHubExtensionEntity> findByRelayHubId(UUID relayHubId);
}
