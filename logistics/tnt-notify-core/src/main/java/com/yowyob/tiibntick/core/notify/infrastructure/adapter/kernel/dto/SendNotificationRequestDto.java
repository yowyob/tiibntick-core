package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Wire DTO for {@code POST /api/notifications/deliveries} on the Kernel
 * (RT-comops) notification engine. Field names mirror the Kernel's
 * {@code SendNotificationRequest} OpenAPI schema exactly.
 *
 * @author MANFOUO Braun
 */
public record SendNotificationRequestDto(
        UUID recipientUserId,
        String recipientAddress,
        String channel,
        String templateCode,
        String subject,
        String body,
        Map<String, Object> variables,
        Map<String, Object> metadata) {
}
