package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.AddressResult;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port — OpenStreetMap Nominatim geocoding API client.
 *
 * Author: MANFOUO Braun
 */
public interface INominatimClient {

    Mono<AddressResult> geocode(String address, String cityCode);

    Mono<AddressResult> reverseGeocode(GeoPoint point);

    Flux<AddressResult> searchNearby(GeoPoint center, double radiusKm);
}
