package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire DTO mirroring the Kernel's {@code NotificationTemplate} view schema.
 *
 * @author MANFOUO Braun
 */
public record NotificationTemplateDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String code,
        String channel,
        String locale,
        String subjectTemplate,
        String bodyTemplate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
