package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.PointOfInterest;
import com.yowyob.tiibntick.core.geo.domain.model.PoiType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — persistence operations for PointOfInterest.
 *
 * Author: MANFOUO Braun
 */
public interface IPointOfInterestRepository {

    Mono<PointOfInterest> save(PointOfInterest poi);

    Mono<PointOfInterest> findById(UUID id, UUID tenantId);

    Flux<PointOfInterest> findByCityCode(UUID tenantId, String cityCode);

    Flux<PointOfInterest> findByType(UUID tenantId, PoiType type);

    Flux<PointOfInterest> findWithinRadius(UUID tenantId, GeoPoint center, double radiusKm);

    Flux<PointOfInterest> findVerifiedByCity(UUID tenantId, String cityCode);
}
