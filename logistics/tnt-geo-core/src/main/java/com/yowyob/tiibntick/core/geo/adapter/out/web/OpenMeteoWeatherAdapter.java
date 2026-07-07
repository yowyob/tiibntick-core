package com.yowyob.tiibntick.core.geo.adapter.out.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.geo.application.port.out.IWeatherApiClient;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.WeatherCondition;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Outbound adapter calling the OpenMeteo free weather API.
 * Fetches hourly precipitation and wind speed for the 5D cost function's
 * xi meteorological factor: delta * xi_tilde.
 *
 * OpenMeteo is free, no API key required, well-suited for Cameroonian coordinates.
 * API docs: https://open-meteo.com/en/docs
 *
 * Author: MANFOUO Braun
 */
@Component
public class OpenMeteoWeatherAdapter implements IWeatherApiClient {

    //private static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com";

    private final WebClient openMeteoWebClient;

    public OpenMeteoWeatherAdapter(WebClient openMeteoWebClient) {
        this.openMeteoWebClient = openMeteoWebClient;
    }

    @Override
    public Mono<WeatherCondition> getCurrentWeather(GeoPoint location) {
        return openMeteoWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", location.latitude())
                        .queryParam("longitude", location.longitude())
                        .queryParam("current", "precipitation,wind_speed_10m")
                        .queryParam("forecast_days", "1")
                        .build())
                .retrieve()
                .bodyToMono(OpenMeteoResponse.class)
                .map(response -> {
                    if (response.current() == null) {
                        return WeatherCondition.clear(Instant.now());
                    }
                    double precip = response.current().precipitation() != null
                            ? response.current().precipitation() : 0.0;
                    double wind = response.current().windSpeed() != null
                            ? response.current().windSpeed() : 0.0;
                    return WeatherCondition.of(precip, wind, Instant.now());
                })
                .onErrorResume(ex -> Mono.just(WeatherCondition.clear(Instant.now())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoResponse(
            CurrentWeather current
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CurrentWeather(
            Double precipitation,
            @JsonProperty("wind_speed_10m") Double windSpeed
    ) {}
}
