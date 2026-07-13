package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.RelayHubExtensionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface R2dbcRelayHubExtensionRepository
        extends ReactiveCrudRepository<RelayHubExtensionEntity, UUID> {

    Mono<RelayHubExtensionEntity> findByRelayHubId(UUID relayHubId);
}
