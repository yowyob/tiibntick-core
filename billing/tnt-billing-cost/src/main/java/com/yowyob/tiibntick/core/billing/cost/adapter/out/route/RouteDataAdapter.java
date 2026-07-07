package com.yowyob.tiibntick.core.billing.cost.adapter.out.route;

import com.yowyob.tiibntick.core.billing.cost.application.port.out.IRouteDataPort;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.RoadType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * RouteDataAdapter — calls tnt-route-core and tnt-geo-core HTTP APIs
 * to fetch route distance, duration, road type, and weather condition.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteDataAdapter implements IRouteDataPort {

    private final WebClient routeCoreWebClient;
    private final WebClient geoCoreWebClient;

    @Override
    public Mono<Double> getRouteDistanceKm(String missionId) {
        return routeCoreWebClient.get()
                .uri("/routes/missions/{id}/distance", missionId)
                .retrieve()
                .bodyToMono(Double.class)
                .doOnError(e -> log.warn("Failed to fetch distance for missionId={}: {}", missionId, e.getMessage()))
                .onErrorReturn(5.0); // fallback distance
    }

    @Override
    public Mono<Integer> getRouteDurationMin(String missionId) {
        return routeCoreWebClient.get()
                .uri("/routes/missions/{id}/duration", missionId)
                .retrieve()
                .bodyToMono(Integer.class)
                .doOnError(e -> log.warn("Failed to fetch duration for missionId={}: {}", missionId, e.getMessage()))
                .onErrorReturn(20); // fallback duration
    }

    @Override
    public Mono<RoadType> getDominantRoadType(String missionId) {
        return routeCoreWebClient.get()
                .uri("/routes/missions/{id}/road-type", missionId)
                .retrieve()
                .bodyToMono(String.class)
                .map(s -> {
                    try {
                        return RoadType.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return RoadType.URBAN_PAVED;
                    }
                })
                .onErrorReturn(RoadType.URBAN_PAVED);
    }

    @Override
    public Mono<WeatherCondition> getWeatherCondition(String missionId, UUID tenantId) {
        return geoCoreWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/weather/missions/{id}")
                        .queryParam("tenantId", tenantId)
                        .build(missionId))
                .retrieve()
                .bodyToMono(String.class)
                .map(s -> {
                    try {
                        return WeatherCondition.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return WeatherCondition.CLEAR;
                    }
                })
                .onErrorReturn(WeatherCondition.CLEAR);
    }
}
