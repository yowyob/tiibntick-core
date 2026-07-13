package com.yowyob.tiibntick.core.linkback.application.port.out;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DaoZoneRepository {

    Mono<DaoZone> save(DaoZone zone);

    Mono<DaoZone> findById(UUID tenantId, UUID zoneId);

    Flux<DaoZone> findActiveByTenant(UUID tenantId);

    /** Active zones whose bounding box (derived from each zone's own center/radius) contains
     * the point — a cheap DB-side pre-filter; callers must still apply the precise circle
     * containment check since a bounding box is a superset of a circle. */
    Flux<DaoZone> findActiveContaining(UUID tenantId, double lat, double lng);
}
