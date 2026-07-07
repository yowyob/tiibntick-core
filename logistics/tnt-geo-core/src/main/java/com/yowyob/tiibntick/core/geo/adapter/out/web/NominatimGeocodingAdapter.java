package com.yowyob.tiibntick.core.geo.adapter.out.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.geo.application.port.out.INominatimClient;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.model.AddressResult;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter calling the OpenStreetMap Nominatim geocoding API.
 * Nominatim provides address-to-coordinate and coordinate-to-address conversion
 * based on OSM data, including African urban areas.
 *
 * Rate limit: max 1 req/s per Nominatim usage policy.
 * The WebClient is configured with rate-limiting and retry in {@link com.yowyob.tiibntick.core.geo.config.WebClientGeoConfig}.
 *
 * Author: MANFOUO Braun
 */
@Component
public class NominatimGeocodingAdapter implements INominatimClient {

    //private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "TiiBnTick/2.0 (logistics@tiibntick.com)";

    private final WebClient nominatimWebClient;

    public NominatimGeocodingAdapter(WebClient nominatimWebClient) {
        this.nominatimWebClient = nominatimWebClient;
    }

    @Override
    public Mono<AddressResult> geocode(String address, String cityCode) {
        String query = address + ", " + cityCode + ", Cameroon";
        return nominatimWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("limit", "1")
                        .queryParam("addressdetails", "1")
                        .build())
                .header("User-Agent", USER_AGENT)
                .retrieve()
                .bodyToFlux(NominatimSearchResult.class)
                .next()
                .map(r -> AddressResult.of(
                        address,
                        GeoPoint.of(Double.parseDouble(r.lat()), Double.parseDouble(r.lon())),
                        r.displayName(),
                        cityCode,
                        computeConfidence(r.importance())
                ))
                .switchIfEmpty(Mono.error(new GeoNotFoundException("address", query)));
    }

    @Override
    public Mono<AddressResult> reverseGeocode(GeoPoint point) {
        return nominatimWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/reverse")
                        .queryParam("lat", point.latitude())
                        .queryParam("lon", point.longitude())
                        .queryParam("format", "json")
                        .build())
                .header("User-Agent", USER_AGENT)
                .retrieve()
                .bodyToMono(NominatimReverseResult.class)
                .map(r -> AddressResult.of(
                        point.toWkt(),
                        point,
                        r.displayName(),
                        extractCityCode(r),
                        0.9
                ));
    }

    @Override
    public Flux<AddressResult> searchNearby(GeoPoint center, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(center.latitude())));
        double south = center.latitude() - latDelta;
        double north = center.latitude() + latDelta;
        double west  = center.longitude() - lonDelta;
        double east  = center.longitude() + lonDelta;
        String viewbox = west + "," + south + "," + east + "," + north;

        return nominatimWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", "")
                        .queryParam("format", "json")
                        .queryParam("viewbox", viewbox)
                        .queryParam("bounded", "1")
                        .queryParam("limit", "20")
                        .build())
                .header("User-Agent", USER_AGENT)
                .retrieve()
                .bodyToFlux(NominatimSearchResult.class)
                .map(r -> AddressResult.of(
                        r.displayName(),
                        GeoPoint.of(Double.parseDouble(r.lat()), Double.parseDouble(r.lon())),
                        r.displayName(),
                        "UNKNOWN",
                        computeConfidence(r.importance())
                ));
    }

    private double computeConfidence(Double importance) {
        if (importance == null) return 0.5;
        return Math.min(importance, 1.0);
    }

    private String extractCityCode(NominatimReverseResult result) {
        if (result.address() != null) {
            if (result.address().city() != null) return result.address().city().toUpperCase().substring(0, Math.min(3, result.address().city().length()));
            if (result.address().town() != null) return result.address().town().toUpperCase().substring(0, Math.min(3, result.address().town().length()));
        }
        return "UNKNOWN";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimSearchResult(
            String lat,
            String lon,
            @JsonProperty("display_name") String displayName,
            Double importance
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimReverseResult(
            @JsonProperty("display_name") String displayName,
            NominatimAddress address
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimAddress(
            String city,
            String town,
            String village,
            String country
    ) {}
}
