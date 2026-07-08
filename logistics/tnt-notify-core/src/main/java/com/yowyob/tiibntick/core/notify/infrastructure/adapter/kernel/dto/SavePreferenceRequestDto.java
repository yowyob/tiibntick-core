package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

import java.util.UUID;

/**
 * Wire DTO for {@code POST /api/notifications/preferences}. One row per
 * (userId, channel) pair — the Kernel has no notion of a single
 * multi-channel preference aggregate.
 *
 * @author MANFOUO Braun
 */
public record SavePreferenceRequestDto(
        UUID userId,
        String channel,
        boolean enabled,
        String locale) {
}
