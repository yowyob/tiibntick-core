package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationAnnonce;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationAnnonceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter bridging the domain NotificationAnnonceRepository port to the reactive R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class NotificationAnnonceRepositoryAdapter implements NotificationAnnonceRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NotificationAnnonceRepository r2dbcRepository;

    @Override
    public Mono<NotificationAnnonce> save(NotificationAnnonce notification) {
        return r2dbcRepository.save(notification);
    }

    @Override
    public Mono<NotificationAnnonce> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
