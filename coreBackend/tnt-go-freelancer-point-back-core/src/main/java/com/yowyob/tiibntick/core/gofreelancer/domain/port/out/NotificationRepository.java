package com.yowyob.tiibntick.core.gofreelancer.domain.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import reactor.core.publisher.Mono;

/**
 * Outbound port for notification persistence operations.
 */
public interface NotificationRepository {

    Mono<Notification> save(Notification notification);
}
