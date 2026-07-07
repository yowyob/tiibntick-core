package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Mono;

/**
 * Secondary port (write side) for notification persistence.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface INotificationRepositoryPort {

    /**
     * Saves or updates a notification and returns the persisted version.
     */
    Mono<Notification> save(Notification notification);
}
