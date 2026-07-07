package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Secondary port (read side) for notification queries.
 *
 * @author MANFOUO Braun
 */
public interface ISearchNotificationsPort {

    Mono<Notification> findById(String notificationId);

    Flux<Notification> findByRecipientId(String recipientId);

    Flux<Notification> findByStatus(DeliveryStatus status);

    Flux<Notification> findPendingByRecipient(String recipientId);
}
