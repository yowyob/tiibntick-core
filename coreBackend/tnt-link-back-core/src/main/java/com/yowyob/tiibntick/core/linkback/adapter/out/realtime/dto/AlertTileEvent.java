package com.yowyob.tiibntick.core.linkback.adapter.out.realtime.dto;

import java.time.Instant;
import java.util.UUID;

/** Payload broadcast on {@code /topic/link/tile/{geohash}} when an alert is reported or resolved. */
public record AlertTileEvent(
        UUID alertId,
        UUID tenantId,
        String type,
        String severity,
        String status,
        double latitude,
        double longitude,
        Instant updatedAt) {
}
