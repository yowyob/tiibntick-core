package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire DTO mirroring the Kernel's {@code NotificationDelivery} view schema —
 * the response of {@code GET/POST /api/notifications/deliveries}.
 *
 * @author MANFOUO Braun
 */
public record NotificationDeliveryDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID recipientUserId,
        String recipientAddress,
        String channel,
        String templateCode,
        String subject,
        String body,
        Map<String, Object> variables,
        Map<String, Object> metadata,
        String status,
        String providerType,
        String providerMessageId,
        String errorMessage,
        Instant requestedAt,
        Instant sentAt) {
}
