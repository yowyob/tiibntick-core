package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.PointOfInterest;
import com.yowyob.tiibntick.core.geo.domain.model.PoiType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port — management of local Points of Interest used as geocoding anchors.
 *
 * Author: MANFOUO Braun
 */
public interface IManagePointsOfInterestUseCase {

    Mono<PointOfInterest> createPoi(UUID tenantId, String name, PoiType type,
                                    GeoPoint coordinates, String description, String cityCode);

    Mono<PointOfInterest> verifyPoi(UUID poiId, UUID tenantId);

    Flux<PointOfInterest> findPoisByCity(UUID tenantId, String cityCode);

    Flux<PointOfInterest> findPoisNearby(GeoPoint center, double radiusKm, UUID tenantId);

    Flux<PointOfInterest> findPoisByType(UUID tenantId, PoiType type);

    Mono<PointOfInterest> findPoi(UUID poiId, UUID tenantId);
}
