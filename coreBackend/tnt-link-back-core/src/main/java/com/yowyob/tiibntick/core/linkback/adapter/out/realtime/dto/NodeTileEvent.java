package com.yowyob.tiibntick.core.linkback.adapter.out.realtime.dto;

import java.time.Instant;
import java.util.UUID;

/** Payload broadcast on {@code /topic/link/tile/{geohash}} when a network node changes tile. */
public record NodeTileEvent(
        UUID nodeId,
        UUID tenantId,
        String refType,
        UUID refId,
        String status,
        double latitude,
        double longitude,
        Double heading,
        double trustScore,
        int gamificationLevel,
        Instant updatedAt) {
}
