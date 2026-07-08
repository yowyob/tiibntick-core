package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire DTO mirroring the Kernel's {@code NotificationReminder} view schema.
 *
 * @author MANFOUO Braun
 */
public record NotificationReminderDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String templateCode,
        String channel,
        UUID recipientUserId,
        String recipientAddress,
        Instant dueAt,
        Boolean active,
        Map<String, Object> variables,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {
}
