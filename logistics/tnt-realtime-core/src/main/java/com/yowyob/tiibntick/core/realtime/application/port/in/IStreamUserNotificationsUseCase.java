package com.yowyob.tiibntick.core.realtime.application.port.in;

import reactor.core.publisher.Flux;

/**
 * Use case for following, as a live stream, the notifications addressed to one user.
 *
 * <p>It is the reading side of {@link IBroadcastNotificationUseCase#broadcastToUser}: whatever is
 * broadcast to a user is emitted here to every connected client of that user, on any application
 * instance (the transport is the Redis channel of the user topic, not an in-memory sink).</p>
 */
public interface IStreamUserNotificationsUseCase {

    /**
     * Live stream of the notifications addressed to the user, as the JSON documents that were broadcast.
     * The stream stays open until the client disconnects and carries no backlog: a notification broadcast
     * while the user is offline is not replayed (the notification history is notify-core's).
     *
     * @param userId the target user's identifier
     * @return hot Flux of JSON notification documents
     */
    Flux<String> streamNotificationsOf(String userId);
}
