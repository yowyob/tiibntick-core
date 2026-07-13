package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record DaoZoneResponse(
        UUID id,
        String name,
        String description,
        double centerLatitude,
        double centerLongitude,
        double radiusKm,
        String status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
