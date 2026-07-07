package com.yowyob.tiibntick.core.realtime.domain.exception;

/**
 * Thrown when a geofence zone cannot be found by its identifier.
 *
 * @author MANFOUO Braun
 */
public class GeofenceZoneNotFoundException extends RealtimeException {

    public GeofenceZoneNotFoundException(String zoneId) {
        super("REALTIME_GEOFENCE_ZONE_NOT_FOUND",
              "Geofence zone not found: " + zoneId);
    }
}
