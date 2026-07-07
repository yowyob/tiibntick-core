package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.WeatherCondition;
import reactor.core.publisher.Mono;

/**
 * Outbound port — real-time weather data provider (OpenMeteo API).
 * Provides the xi meteorological factor for the 5D cost function.
 *
 * Author: MANFOUO Braun
 */
public interface IWeatherApiClient {

    /**
     * Fetches current weather at the given coordinates.
     * Falls back to {@link WeatherCondition#clear(java.time.Instant)} if the API is unavailable.
     */
    Mono<WeatherCondition> getCurrentWeather(GeoPoint location);
}
