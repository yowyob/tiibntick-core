package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

import java.time.Instant;

/**
 * Read-only mirror of a delivery tracked by the Kernel notification engine —
 * distinct from the local {@link Notification} aggregate, which tracks
 * TiiBnTick's own send pipeline. Exposed for admin/troubleshooting
 * visibility into what the Kernel actually attempted.
 *
 * @author MANFOUO Braun
 */
public record NotificationDeliveryRecord(
        String id,
        String recipientUserId,
        String recipientAddress,
        NotificationChannel channel,
        String templateCode,
        String subject,
        String body,
        String status,
        String providerType,
        String providerMessageId,
        String errorMessage,
        Instant requestedAt,
        Instant sentAt) {
}
