package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
}
