package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryDaoZonesUseCase {

    Mono<DaoZone> findById(UUID tenantId, UUID zoneId);

    Flux<DaoZone> findActiveByTenant(UUID tenantId);

    /** Active zones whose circle contains {@code point}. */
    Flux<DaoZone> findContaining(UUID tenantId, GeoPoint point);
}
