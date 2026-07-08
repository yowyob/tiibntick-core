package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto;

/**
 * Wire DTO for {@code POST /api/notifications/providers} — configures a
 * physical delivery provider (SMTP, Twilio, Meta WhatsApp, Firebase...) on
 * the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public record SaveProviderRequestDto(
        String channel,
        String type,
        String name,
        Boolean defaultProvider,
        Boolean active,
        String configurationJson) {
}
