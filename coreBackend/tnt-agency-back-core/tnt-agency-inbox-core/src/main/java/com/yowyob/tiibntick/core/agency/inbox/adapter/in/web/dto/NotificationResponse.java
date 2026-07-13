package com.yowyob.tiibntick.core.agency.inbox.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String eventType,
        String title,
        String body,
        String href,
        boolean read,
        Instant createdAt) {}
