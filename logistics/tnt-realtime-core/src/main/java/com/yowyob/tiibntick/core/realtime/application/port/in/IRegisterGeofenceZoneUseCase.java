package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import reactor.core.publisher.Mono;

/**
 * Use case for managing geofence zones used for real-time monitoring.
 *
 * @author MANFOUO Braun
 */
public interface IRegisterGeofenceZoneUseCase {

    /**
     * Registers a new geofence zone for real-time monitoring.
     *
     * @param zone the zone to register
     * @return Mono completing after persistence
     */
    Mono<Void> registerZone(GeofenceZone zone);

    /**
     * Removes a geofence zone from monitoring.
     *
     * @param zoneId   the zone identifier
     * @param tenantId the tenant context
     * @return Mono completing after removal
     */
    Mono<Void> removeZone(String zoneId, String tenantId);
}
