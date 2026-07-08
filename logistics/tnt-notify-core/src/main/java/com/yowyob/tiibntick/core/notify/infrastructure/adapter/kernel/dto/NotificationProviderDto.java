package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire DTO mirroring the Kernel's {@code NotificationProvider} view schema.
 *
 * @author MANFOUO Braun
 */
public record NotificationProviderDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String channel,
        String type,
        String name,
        Boolean defaultProvider,
        Boolean active,
        String configurationJson,
        Instant createdAt,
        Instant updatedAt) {
}
