package com.yowyob.tiibntick.core.realtime.application.port.out;

import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for persisting and querying geofence zones.
 * Zones are stored in Redis for fast in-memory lookup during GPS ping processing.
 *
 * @author MANFOUO Braun
 */
public interface IGeofenceZoneRepository {

    /**
     * Persists a geofence zone.
     *
     * @param zone the zone to save
     * @return Mono with the saved zone
     */
    Mono<GeofenceZone> save(GeofenceZone zone);

    /**
     * Returns all active geofence zones for a tenant.
     *
     * @param tenantId the tenant context
     * @return Flux of active zones
     */
    Flux<GeofenceZone> findActiveByTenant(String tenantId);

    /**
     * Deletes a specific geofence zone.
     *
     * @param zoneId   the zone identifier
     * @param tenantId the tenant context
     * @return Mono completing after deletion
     */
    Mono<Void> deleteByIdAndTenant(String zoneId, String tenantId);
}
