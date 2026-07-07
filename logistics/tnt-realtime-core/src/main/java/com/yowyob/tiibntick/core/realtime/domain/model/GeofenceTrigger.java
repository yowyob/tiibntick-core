package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceDirection;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object emitted whenever a deliverer crosses the boundary of a geofence zone.
 * Consumed by tnt-delivery-core to trigger automatic mission state transitions
 * (e.g. approaching relay hub → auto-deposit flow start).
 *
 * @author MANFOUO Braun
 */
public record GeofenceTrigger(
        String actorId,
        String tenantId,
        String zoneId,
        String zoneName,
        GeofenceZoneType zoneType,
        String linkedEntityId,
        GeofenceDirection direction,
        GeoCoordinates coordinates,
        String missionId,
        LocalDateTime triggeredAt
) {

    public GeofenceTrigger {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }

    public static GeofenceTrigger of(
            String actorId, String tenantId,
            GeofenceZone zone,
            GeofenceDirection direction,
            GeoCoordinates coordinates,
            String missionId) {

        return new GeofenceTrigger(
                actorId, tenantId,
                zone.getId(), zone.getName(), zone.getType(), zone.getLinkedEntityId(),
                direction, coordinates, missionId,
                LocalDateTime.now());
    }

    public boolean isEntry() {
        return direction == GeofenceDirection.ENTER;
    }

    public boolean isExit() {
        return direction == GeofenceDirection.EXIT;
    }

    @Override
    public String toString() {
        return "GeofenceTrigger{actor=" + actorId + ", zone=" + zoneName + ", direction=" + direction + "}";
    }
}
