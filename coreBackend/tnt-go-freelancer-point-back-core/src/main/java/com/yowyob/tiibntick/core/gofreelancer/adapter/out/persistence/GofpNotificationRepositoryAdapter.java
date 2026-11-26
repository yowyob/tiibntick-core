package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter: bridges the domain NotificationRepository port to the
 * R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class GofpNotificationRepositoryAdapter implements NotificationRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NotificationRepository r2dbcRepository;

    @Override
    public Mono<Notification> save(Notification notification) {
        return r2dbcRepository.save(notification);
    }

    @Override
    public Mono<Notification> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Boolean> existsByEventKey(String eventKey) {
        return r2dbcRepository.existsByEventKey(eventKey);
    }

    @Override
    public Flux<Notification> findInbox(UUID personId, int limit, long offset) {
        return r2dbcRepository.findInbox(personId, limit, offset);
    }

    @Override
    public Mono<Long> countUnread(UUID personId) {
        return r2dbcRepository.countUnread(personId);
    }

    @Override
    public Mono<Long> markAllRead(UUID personId, Instant readAt) {
        return r2dbcRepository.markAllRead(personId, readAt);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Mono<Long> deleteExpired(Instant readBefore, Instant createdBefore) {
        return r2dbcRepository.deleteExpired(readBefore, createdBefore);
    }
}
