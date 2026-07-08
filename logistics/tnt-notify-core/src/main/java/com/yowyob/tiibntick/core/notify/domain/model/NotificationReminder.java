package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A future notification scheduled on the Kernel notification engine
 * (e.g. "remind this user in 48h about pending KYC documents").
 *
 * @author MANFOUO Braun
 */
public record NotificationReminder(
        UUID id,
        String templateCode,
        NotificationChannel channel,
        String recipientUserId,
        String recipientAddress,
        Instant dueAt,
        boolean active,
        Map<String, Object> variables,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static NotificationReminder of(String templateCode, NotificationChannel channel,
            String recipientUserId, String recipientAddress, Instant dueAt,
            Map<String, Object> variables, Map<String, Object> metadata) {
        return new NotificationReminder(null, templateCode, channel, recipientUserId, recipientAddress,
                dueAt, true, variables, metadata, null, null);
    }
}
