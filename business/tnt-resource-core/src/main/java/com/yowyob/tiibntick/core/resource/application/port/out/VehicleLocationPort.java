package com.yowyob.tiibntick.core.resource.application.port.out;

import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Outbound port: fast-path GPS location cache for vehicles (backed by Redis).
 * Provides sub-millisecond read for real-time tracking dashboards.
 * @author MANFOUO Braun.
 */
public interface VehicleLocationPort {

    /**
     * Stores the latest GPS location for a vehicle in the cache.
     */
    Mono<Void> updateLocation(UUID vehicleId, double latitude, double longitude);

    /**
     * Retrieves the last known GPS location [latitude, longitude] for a vehicle.
     * Returns empty Mono if no location is cached.
     */
    Mono<double[]> getLocation(UUID vehicleId);

    /**
     * Removes the cached location when a vehicle is retired.
     */
    Mono<Void> evictLocation(UUID vehicleId);
}
