package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire DTO for {@code POST /api/notifications/reminders} — schedules a
 * future notification on the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public record SaveReminderRequestDto(
        String templateCode,
        String channel,
        UUID recipientUserId,
        String recipientAddress,
        Instant dueAt,
        Boolean active,
        Map<String, Object> variables,
        Map<String, Object> metadata) {
}
