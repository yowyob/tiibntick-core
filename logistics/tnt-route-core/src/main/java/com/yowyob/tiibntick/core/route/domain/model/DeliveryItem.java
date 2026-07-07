package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Instant;

public record DeliveryItem(
        String id,
        String pickupNodeId,
        String dropoffNodeId,
        double weightKg,
        int priority,
        Instant deadline
) {
    public DeliveryItem {
        if (weightKg < 0) throw new IllegalArgumentException("weightKg must be >= 0");
        if (priority < 0) throw new IllegalArgumentException("priority must be >= 0");
    }
}
