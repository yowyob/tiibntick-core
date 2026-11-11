package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NegotiationMessage;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface NegotiationMessageRepository extends ReactiveCrudRepository<NegotiationMessage, UUID> {

    Flux<NegotiationMessage> findByAnnouncementIdAndThreadUserIdOrderByCreatedAtAsc(UUID announcementId, UUID threadUserId);

    Mono<NegotiationMessage> findFirstByAnnouncementIdAndThreadUserIdOrderByCreatedAtDesc(UUID announcementId, UUID threadUserId);

    /** Marks as read every message of the thread that the reader did not write and had not read yet. */
    @Modifying
    @Query("""
            UPDATE gofp_negotiation_messages SET read_at = :readAt
            WHERE announcement_id = :announcementId AND thread_user_id = :threadUserId
              AND sender_user_id <> :readerId AND read_at IS NULL
            """)
    Mono<Long> markRead(@Param("announcementId") UUID announcementId, @Param("threadUserId") UUID threadUserId,
                        @Param("readerId") UUID readerId, @Param("readAt") Instant readAt);

    /** Messages of the thread the reader has not read yet (those written by the other party). */
    @Query("""
            SELECT COUNT(*) FROM gofp_negotiation_messages
            WHERE announcement_id = :announcementId AND thread_user_id = :threadUserId
              AND sender_user_id <> :readerId AND read_at IS NULL
            """)
    Mono<Long> countUnread(@Param("announcementId") UUID announcementId, @Param("threadUserId") UUID threadUserId,
                           @Param("readerId") UUID readerId);
}
