package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Query side of the notification history (served by the Kernel).
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface ISearchNotificationsUseCase {

    Flux<Notification> findByRecipientId(String tenantId, String organizationId, String recipientId);

    Flux<Notification> findByStatus(String tenantId, String organizationId, DeliveryStatus status);

    Mono<Notification> findById(String tenantId, String organizationId, String notificationId);

    Flux<Notification> findPendingByRecipient(String tenantId, String organizationId, String recipientId);
}
