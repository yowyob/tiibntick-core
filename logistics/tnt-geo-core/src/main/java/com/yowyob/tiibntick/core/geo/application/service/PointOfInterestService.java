package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IManagePointsOfInterestUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.IPointOfInterestRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.PointOfInterest;
import com.yowyob.tiibntick.core.geo.domain.model.PoiType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service managing Points of Interest for TiiBnTick's African context.
 * POIs serve as geocoding anchors (landmark-based addressing) and routing hints.
 *
 * Author: MANFOUO Braun
 */
@Service
public class PointOfInterestService implements IManagePointsOfInterestUseCase {

    private final IPointOfInterestRepository poiRepository;

    public PointOfInterestService(IPointOfInterestRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    @Override
    public Mono<PointOfInterest> createPoi(UUID tenantId, String name, PoiType type,
                                            GeoPoint coordinates, String description, String cityCode) {
        PointOfInterest poi = PointOfInterest.create(tenantId, name, type, coordinates, description, cityCode);
        return poiRepository.save(poi);
    }

    @Override
    public Mono<PointOfInterest> verifyPoi(UUID poiId, UUID tenantId) {
        return poiRepository.findById(poiId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("PointOfInterest", poiId.toString())))
                .flatMap(poi -> {
                    poi.verify();
                    return poiRepository.save(poi);
                });
    }

    @Override
    public Flux<PointOfInterest> findPoisByCity(UUID tenantId, String cityCode) {
        return poiRepository.findByCityCode(tenantId, cityCode.toUpperCase());
    }

    @Override
    public Flux<PointOfInterest> findPoisNearby(GeoPoint center, double radiusKm, UUID tenantId) {
        if (radiusKm <= 0) {
            return Flux.error(new IllegalArgumentException("radiusKm must be > 0"));
        }
        return poiRepository.findWithinRadius(tenantId, center, radiusKm);
    }

    @Override
    public Flux<PointOfInterest> findPoisByType(UUID tenantId, PoiType type) {
        return poiRepository.findByType(tenantId, type);
    }

    @Override
    public Mono<PointOfInterest> findPoi(UUID poiId, UUID tenantId) {
        return poiRepository.findById(poiId, tenantId)
                .switchIfEmpty(Mono.error(new GeoNotFoundException("PointOfInterest", poiId.toString())));
    }
}
