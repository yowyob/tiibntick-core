package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for Notification entity operations.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Repository
public interface NotificationRepository extends ReactiveCrudRepository<Notification, UUID> {

    Mono<Boolean> existsByEventKey(String eventKey);

    /** Lot C-36 — inbox page: unread first, then newest first. */
    @Query("""
            SELECT * FROM notifications WHERE person_id = :personId
            ORDER BY (read_at IS NULL) DESC, created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<Notification> findInbox(UUID personId, int limit, long offset);

    @Query("SELECT COUNT(*) FROM notifications WHERE person_id = :personId AND read_at IS NULL")
    Mono<Long> countUnread(UUID personId);

    @Modifying
    @Query("""
            UPDATE notifications SET read_at = :readAt, notification_status = 'READ'
            WHERE person_id = :personId AND read_at IS NULL
            """)
    Mono<Long> markAllRead(UUID personId, Instant readAt);

    @Modifying
    @Query("""
            DELETE FROM notifications
            WHERE (read_at IS NOT NULL AND read_at < :readBefore) OR created_at < :createdBefore
            """)
    Mono<Long> deleteExpired(Instant readBefore, Instant createdBefore);
}
