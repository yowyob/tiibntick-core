package com.yowyob.tiibntick.core.incident.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable geographic snapshot captured at incident detection time, including zone risk index.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value
@Builder
public class IncidentGeoSnapshot {
    UUID incidentId;
    double latitude;
    double longitude;
    Instant capturedAt;
    String addressLabel;
    UUID nearestHubId;
    double nearestHubDistanceKm;
    double zoneRiskIndex;

    public boolean isHighRiskZone() {
        return zoneRiskIndex >= 0.7;
    }
}
