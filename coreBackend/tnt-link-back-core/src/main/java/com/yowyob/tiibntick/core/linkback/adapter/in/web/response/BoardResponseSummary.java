package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record BoardResponseSummary(
        UUID id,
        UUID deliveryPersonId,
        Instant estimatedArrivalTime,
        String note,
        String status
) {
}
