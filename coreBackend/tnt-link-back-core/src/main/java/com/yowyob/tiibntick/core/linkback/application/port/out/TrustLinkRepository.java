package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrustLinkRepository {

    Mono<TrustLink> save(TrustLink link);

    Mono<Boolean> existsByFromAndTo(UUID tenantId, UUID fromNodeId, UUID toNodeId);

    Flux<TrustLink> findByToNodeId(UUID tenantId, UUID toNodeId);

    Mono<Long> countByToNodeId(UUID tenantId, UUID toNodeId);
}
