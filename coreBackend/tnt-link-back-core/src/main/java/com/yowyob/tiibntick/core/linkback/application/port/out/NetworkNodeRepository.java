package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface NetworkNodeRepository {

    Mono<NetworkNode> save(NetworkNode node);

    Mono<NetworkNode> findById(UUID tenantId, UUID nodeId);

    Mono<NetworkNode> findByRefId(UUID tenantId, UUID refId);

    /** Batch lookup — single query for {@code /by-ref/batch} (Audit n6 S27). */
    Flux<NetworkNode> findByRefIds(UUID tenantId, Collection<UUID> refIds);

    Flux<NetworkNode> findWithinBoundingBox(UUID tenantId, double minLat, double minLng, double maxLat, double maxLng);

    /** Ranked by trust score then gamification points, descending. */
    Flux<NetworkNode> findTopRanked(UUID tenantId, int limit);
}
