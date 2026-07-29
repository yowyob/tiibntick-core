package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for delivery person location update use cases.
 * Delegates the full GPS ping pipeline (Kalman ETA, Geofences, SSE/WS broadcast)
 * to the centralized tnt-realtime-core module.
 */
public interface FreelancerLocationUseCase {

    /**
     * Processes a GPS ping from a freelancer's device.
     *
     * @param freelancerId  UUID of the freelancer (deliverer)
     * @param tenantId      tenant identifier (required by realtime-core)
     * @param latitude      WGS-84 latitude
     * @param longitude     WGS-84 longitude
     * @param speedKmh      current speed in km/h (0.0 if unknown)
     * @param bearing       heading in degrees [0, 360] (0.0 if unknown)
     * @param accuracy      GPS accuracy in meters (0.0 if unknown)
     * @param missionId     optional active mission ID (nullable)
     * @param freelancerOrgId optional FreelancerOrg UUID for fleet tracking (nullable)
     * @return Mono completing when the full pipeline has been triggered
     */
    Mono<Void> updateLocation(UUID freelancerId,
                              String tenantId,
                              double latitude,
                              double longitude,
                              double speedKmh,
                              double bearing,
                              double accuracy,
                              String missionId,
                              String freelancerOrgId);
}
