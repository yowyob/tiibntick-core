package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

/**
 * Wire DTO for {@code POST /api/notifications/templates} — registers a
 * channel-specific message template on the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public record SaveTemplateRequestDto(
        String code,
        String channel,
        String locale,
        String subjectTemplate,
        String bodyTemplate,
        Boolean active) {
}
