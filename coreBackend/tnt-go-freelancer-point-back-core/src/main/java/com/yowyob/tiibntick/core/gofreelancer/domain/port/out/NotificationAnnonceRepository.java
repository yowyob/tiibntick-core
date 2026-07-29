package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationAnnonce;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for announcement notification persistence operations.
 */
public interface NotificationAnnonceRepository {

    Mono<NotificationAnnonce> save(NotificationAnnonce notification);

    Mono<NotificationAnnonce> findById(UUID id);

    Mono<Void> deleteById(UUID id);
}
