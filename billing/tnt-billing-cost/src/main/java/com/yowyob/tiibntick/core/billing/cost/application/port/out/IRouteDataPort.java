package com.yowyob.tiibntick.core.billing.cost.application.port.out;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.RoadType;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.WeatherCondition;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Secondary port — fetches route and road-condition data from tnt-route-core and tnt-geo-core.
 * @author MANFOUO Braun
 */
public interface IRouteDataPort {

    /**
     * Fetches the total arc distance for a mission route.
     * @param deliveryId mission identifier
     * @return distance in kilometres
     */
    Mono<Double> getRouteDistanceKm(String deliveryId);

    /**
     * Fetches the estimated travel duration for a mission route.
     * @param deliveryId mission identifier
     * @return duration in minutes
     */
    Mono<Integer> getRouteDurationMin(String deliveryId);

    /**
     * Fetches the dominant road type for the mission route.
     * @param deliveryId mission identifier
     * @return RoadType
     */
    Mono<RoadType> getDominantRoadType(String deliveryId);

    /**
     * Fetches the current or forecast weather condition for a mission.
     * @param deliveryId mission identifier
     * @param tenantId  tenant context (for geo-fencing)
     * @return WeatherCondition
     */
    Mono<WeatherCondition> getWeatherCondition(String deliveryId, UUID tenantId);
}
