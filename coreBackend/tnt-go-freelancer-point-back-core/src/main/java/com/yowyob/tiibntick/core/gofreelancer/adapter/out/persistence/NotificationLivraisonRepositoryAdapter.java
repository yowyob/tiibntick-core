package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationLivraison;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.NotificationLivraisonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter bridging the domain NotificationLivraisonRepository port to the reactive R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class NotificationLivraisonRepositoryAdapter implements NotificationLivraisonRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.NotificationLivraisonRepository r2dbcRepository;

    @Override
    public Mono<NotificationLivraison> save(NotificationLivraison notification) {
        return r2dbcRepository.save(notification);
    }

    @Override
    public Mono<NotificationLivraison> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
