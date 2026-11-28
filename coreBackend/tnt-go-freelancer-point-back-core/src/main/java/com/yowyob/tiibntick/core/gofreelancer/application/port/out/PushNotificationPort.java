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

    /**
     * A message written once per language in the catalog and rendered with {@code params} in the language of
     * the recipient's account (never a literal built by the caller). Nothing is sent when the key is unknown to
     * the catalog beyond the generic fallback the notification module applies.
     */
    Mono<Void> sendLocalized(UUID tenantId, UUID userId, String templateKey, java.util.Map<String, Object> params);

    default Mono<Void> sendLocalized(UUID userId, String templateKey, java.util.Map<String, Object> params) {
        return sendLocalized(null, userId, templateKey, params);
    }
}
