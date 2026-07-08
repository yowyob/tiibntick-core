package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire DTO mirroring the Kernel's {@code NotificationPreference} view schema.
 *
 * @author MANFOUO Braun
 */
public record NotificationPreferenceDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID userId,
        String channel,
        boolean enabled,
        String locale,
        Instant updatedAt) {
}
