package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for push notification operations.
 */
public interface PushNotificationPort {

    Mono<Void> sendPushNotification(UUID userId, String title, String message);

    /**
     * Same, stored under the given tenant — the one the recipient reads their feed from. The
     * three-argument form falls back to the configured default tenant, which is not the tenant of
     * the user's token, so its notifications are invisible in the feed.
     */
    default Mono<Void> sendPushNotification(UUID tenantId, UUID userId, String title, String message) {
        return sendPushNotification(userId, title, message);
    }
}
