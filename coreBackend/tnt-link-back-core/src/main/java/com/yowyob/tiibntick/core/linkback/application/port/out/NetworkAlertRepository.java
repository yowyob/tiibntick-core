package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NetworkAlertRepository {

    Mono<NetworkAlert> save(NetworkAlert alert);

    Mono<NetworkAlert> findById(UUID tenantId, UUID alertId);

    Flux<NetworkAlert> findActiveByTenant(UUID tenantId);

    /** Active alerts whose lat/lng bounding box overlaps the given range — a cheap DB-side
     * pre-filter; callers must still apply the precise radius check since a bounding box is
     * a superset of a circle. */
    Flux<NetworkAlert> findActiveWithinBoundingBox(UUID tenantId, double minLat, double maxLat, double minLng, double maxLng);
}
