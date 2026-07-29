package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for push notification operations.
 */
public interface PushNotificationPort {

    Mono<Void> sendPushNotification(UUID userId, String title, String message);
}
