package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationLivraison;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for delivery notification persistence operations.
 */
public interface NotificationLivraisonRepository {

    Mono<NotificationLivraison> save(NotificationLivraison notification);

    Mono<NotificationLivraison> findById(UUID id);

    Mono<Void> deleteById(UUID id);
}
