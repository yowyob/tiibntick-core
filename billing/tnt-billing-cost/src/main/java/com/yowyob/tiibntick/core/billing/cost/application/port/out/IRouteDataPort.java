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
     * @param missionId mission identifier
     * @return distance in kilometres
     */
    Mono<Double> getRouteDistanceKm(String missionId);

    /**
     * Fetches the estimated travel duration for a mission route.
     * @param missionId mission identifier
     * @return duration in minutes
     */
    Mono<Integer> getRouteDurationMin(String missionId);

    /**
     * Fetches the dominant road type for the mission route.
     * @param missionId mission identifier
     * @return RoadType
     */
    Mono<RoadType> getDominantRoadType(String missionId);

    /**
     * Fetches the current or forecast weather condition for a mission.
     * @param missionId mission identifier
     * @param tenantId  tenant context (for geo-fencing)
     * @return WeatherCondition
     */
    Mono<WeatherCondition> getWeatherCondition(String missionId, UUID tenantId);
}
