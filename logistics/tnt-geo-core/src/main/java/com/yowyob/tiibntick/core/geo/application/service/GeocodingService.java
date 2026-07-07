package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.in.IGeocodeUseCase;
import com.yowyob.tiibntick.core.geo.application.port.out.INominatimClient;
import com.yowyob.tiibntick.core.geo.application.port.out.IPointOfInterestRepository;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.AddressResult;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for geocoding and reverse-geocoding.
 * Implements a two-tier strategy:
 *   1. Try Nominatim OSM API first.
 *   2. Fall back to local POI search if Nominatim fails or returns low confidence.
 *
 * Author: MANFOUO Braun
 */
@Service
public class GeocodingService implements IGeocodeUseCase {

    private static final double CONFIDENCE_THRESHOLD = 0.4;

    private final INominatimClient nominatimClient;
    private final IPointOfInterestRepository poiRepository;

    public GeocodingService(INominatimClient nominatimClient,
                            IPointOfInterestRepository poiRepository) {
        this.nominatimClient = nominatimClient;
        this.poiRepository = poiRepository;
    }

    @Override
    public Mono<AddressResult> geocode(String address, String cityCode) {
        if (address == null || address.isBlank()) {
            return Mono.error(new IllegalArgumentException("address must not be blank"));
        }
        return nominatimClient.geocode(address, cityCode)
                .flatMap(result -> {
                    if (result.confidence() >= CONFIDENCE_THRESHOLD) {
                        return Mono.just(result);
                    }
                    return fallbackToLocalPoi(address, cityCode, result);
                })
                .onErrorResume(ex -> fallbackToLocalPoiDirect(address, cityCode));
    }

    @Override
    public Mono<AddressResult> reverseGeocode(GeoPoint point) {
        return nominatimClient.reverseGeocode(point)
                .onErrorResume(ex -> Mono.just(
                        AddressResult.of(point.toWkt(), point, "Unknown location", "UNKNOWN", 0.0)
                ));
    }

    @Override
    public Flux<AddressResult> findNearbyAddresses(GeoPoint center, double radiusKm, String tenantId) {
        Flux<AddressResult> poiResults = poiRepository
                .findWithinRadius(
                        tenantId != null ? UUID.fromString(tenantId) : null,
                        center,
                        radiusKm
                )
                .map(poi -> AddressResult.of(
                        poi.name(),
                        poi.coordinates(),
                        poi.name() + " (" + poi.type().name() + ")",
                        poi.cityCode(),
                        poi.isVerified() ? 0.9 : 0.6
                ));

        Flux<AddressResult> nominatimResults = nominatimClient.searchNearby(center, radiusKm)
                .onErrorResume(ex -> Flux.empty());

        return Flux.merge(poiResults, nominatimResults)
                .distinct(AddressResult::displayName)
                .sort((a, b) -> Double.compare(
                        a.coordinates().haversineDistanceTo(center),
                        b.coordinates().haversineDistanceTo(center)
                ));
    }

    private Mono<AddressResult> fallbackToLocalPoi(String address, String cityCode,
                                                    AddressResult nominatimResult) {
        String upperCity = cityCode != null ? cityCode.toUpperCase() : "YDE";
        return poiRepository.findVerifiedByCity(null, upperCity)
                .filter(poi -> poi.name().toLowerCase().contains(address.toLowerCase()))
                .next()
                .map(poi -> AddressResult.of(
                        address,
                        poi.coordinates(),
                        poi.name(),
                        poi.cityCode(),
                        0.75
                ))
                .defaultIfEmpty(nominatimResult);
    }

    private Mono<AddressResult> fallbackToLocalPoiDirect(String address, String cityCode) {
        String upperCity = cityCode != null ? cityCode.toUpperCase() : "YDE";
        return poiRepository.findVerifiedByCity(null, upperCity)
                .filter(poi -> poi.name().toLowerCase().contains(address.toLowerCase()))
                .next()
                .map(poi -> AddressResult.of(address, poi.coordinates(), poi.name(), poi.cityCode(), 0.7))
                .switchIfEmpty(Mono.error(new GeoNotFoundException("address", address)));
    }
}
