package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record NetworkAlertResponse(
        UUID id,
        UUID reporterId,
        String type,
        String description,
        double latitude,
        double longitude,
        String severity,
        String status,
        int confirmCount,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
}
