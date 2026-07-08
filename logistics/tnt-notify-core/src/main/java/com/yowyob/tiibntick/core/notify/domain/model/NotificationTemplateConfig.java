package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * A channel-specific message template (subject/body with placeholders)
 * registered on the Kernel notification engine, referenced by
 * {@code templateCode} when the Kernel should render the message
 * server-side instead of receiving a pre-translated body.
 *
 * @author MANFOUO Braun
 */
public record NotificationTemplateConfig(
        UUID id,
        String code,
        NotificationChannel channel,
        String locale,
        String subjectTemplate,
        String bodyTemplate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static NotificationTemplateConfig of(String code, NotificationChannel channel, String locale,
            String subjectTemplate, String bodyTemplate, boolean active) {
        return new NotificationTemplateConfig(null, code, channel, locale, subjectTemplate, bodyTemplate,
                active, null, null);
    }
}
