package com.yowyob.tiibntick.core.realtime.application.port.out;

import java.time.LocalDateTime;

/**
 * Realtime-owned payload for {@link IGeofenceAnchorPort#anchor}.
 *
 * <p>Deliberately independent from any {@code tnt-trust-core} domain type — the
 * implementing adapter (in {@code tnt-trust-core}) maps this into its own
 * {@code LogisticTrustEvent}, keeping the hexagonal boundary between the two modules.
 *
 * @param direction {@code ENTER} or {@code EXIT}, mirroring {@code GeofenceDirection}
 * @param zoneType  mirrors {@code GeofenceZoneType}, e.g. {@code RELAY_HUB}, {@code DANGER_ZONE}
 * @author MANFOUO Braun
 */
public record GeofenceAnchorPayload(
        String tenantId,
        String actorId,
        String zoneId,
        String zoneName,
        String zoneType,
        String direction,
        double gpsLat,
        double gpsLng,
        String missionId,
        LocalDateTime triggeredAt) {
}
