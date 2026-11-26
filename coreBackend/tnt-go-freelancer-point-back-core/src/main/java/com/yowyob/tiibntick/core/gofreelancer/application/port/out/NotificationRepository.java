package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for notification persistence operations.
 *
 * <p>Lot C-36 — also the in-app inbox: {@code personId} is the recipient's user id.
 */
public interface NotificationRepository {

    Mono<Notification> save(Notification notification);

    Mono<Notification> findById(UUID id);

    Mono<Boolean> existsByEventKey(String eventKey);

    /** The recipient's notifications, unread first, then most recent first. */
    Flux<Notification> findInbox(UUID personId, int limit, long offset);

    Mono<Long> countUnread(UUID personId);

    /** Marks every unread notification of the recipient read; returns how many changed. */
    Mono<Long> markAllRead(UUID personId, Instant readAt);

    Mono<Void> deleteById(UUID id);

    /** Retention: read before {@code readBefore}, or created before {@code createdBefore}. */
    Mono<Long> deleteExpired(Instant readBefore, Instant createdBefore);
}
