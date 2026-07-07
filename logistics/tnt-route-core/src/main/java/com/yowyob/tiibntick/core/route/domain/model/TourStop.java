package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Instant;

public record TourStop(
        String nodeId,
        WaypointType stopType,
        int sequenceOrder,
        Instant estimatedArrival,
        int dwellTimeMinutes,
        String deliveryItemId
) {}
