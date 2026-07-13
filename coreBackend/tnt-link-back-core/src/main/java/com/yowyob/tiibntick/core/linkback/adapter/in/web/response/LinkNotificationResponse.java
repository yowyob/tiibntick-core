package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.time.Instant;

/**
 * Generic Link business representation of a notification — not screen-shaped
 * (that adaptation belongs to the Link BFF).
 */
public record LinkNotificationResponse(
        String id,
        String channel,
        String priority,
        String content,
        String status,
        Instant createdAt,
        Instant sentAt
) {
}
