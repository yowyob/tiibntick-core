package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical delivery provider (SMTP, Twilio, Meta WhatsApp Cloud API,
 * Firebase...) configured on the Kernel notification engine for a given
 * channel.
 *
 * @author MANFOUO Braun
 */
public record NotificationProviderConfig(
        UUID id,
        NotificationChannel channel,
        String type,
        String name,
        boolean defaultProvider,
        boolean active,
        String configurationJson,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Convenience factory for registering a new provider (no id/timestamps yet).
     */
    public static NotificationProviderConfig of(NotificationChannel channel, String type, String name,
            boolean defaultProvider, boolean active, String configurationJson) {
        return new NotificationProviderConfig(null, channel, type, name, defaultProvider, active,
                configurationJson, null, null);
    }
}
