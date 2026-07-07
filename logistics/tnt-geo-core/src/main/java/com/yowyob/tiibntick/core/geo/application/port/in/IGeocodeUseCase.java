package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.AddressResult;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port — geocoding and reverse-geocoding operations.
 * Backed by Nominatim OSM with local African landmark fallback.
 *
 * Author: MANFOUO Braun
 */
public interface IGeocodeUseCase {

    /**
     * Converts a human-readable address string to geographic coordinates.
     *
     * @param address  the raw address or landmark description
     * @param cityCode the ISO or local city code to narrow the search area
     * @return a Mono emitting the best geocoding result
     */
    Mono<AddressResult> geocode(String address, String cityCode);

    /**
     * Converts geographic coordinates back to a human-readable address.
     *
     * @param point the coordinate to reverse-geocode
     * @return a Mono emitting the resolved address description
     */
    Mono<AddressResult> reverseGeocode(GeoPoint point);

    /**
     * Finds all locally-registered Points of Interest near the given point.
     *
     * @param center    the search origin
     * @param radiusKm  the search radius in kilometres
     * @param tenantId  the tenant scope (null for global search)
     * @return a Flux of nearby POI address results ordered by proximity
     */
    Flux<AddressResult> findNearbyAddresses(GeoPoint center, double radiusKm, String tenantId);
}
