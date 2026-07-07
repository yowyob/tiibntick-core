package com.yowyob.tiibntick.core.realtime.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Use case for broadcasting generic notifications to a user or topic via WebSocket.
 * Used by tnt-notify-core to deliver in-app notifications to connected clients.
 *
 * @author MANFOUO Braun
 */
public interface IBroadcastNotificationUseCase {

    /**
     * Broadcasts a notification payload to all sessions subscribed to the given topic.
     *
     * @param topic   the STOMP topic path
     * @param payload the notification payload (will be serialized to JSON)
     * @return Mono completing when the broadcast is dispatched
     */
    Mono<Void> broadcastToTopic(String topic, Object payload);

    /**
     * Broadcasts a notification directly to all sessions of a specific user.
     *
     * @param userId  the target user's identifier
     * @param payload the notification payload
     * @return Mono completing when the broadcast is dispatched
     */
    Mono<Void> broadcastToUser(String userId, Object payload);
}
