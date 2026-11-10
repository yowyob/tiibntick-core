package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads the notification history. The history lives in the Kernel ({@code GET /api/notifications/deliveries}),
 * so every query is scoped by tenant and organization.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface ISearchNotificationsPort {

    Mono<Notification> findById(String tenantId, String organizationId, String notificationId);

    Flux<Notification> findByRecipientId(String tenantId, String organizationId, String recipientId);

    Flux<Notification> findByStatus(String tenantId, String organizationId, DeliveryStatus status);

    Flux<Notification> findPendingByRecipient(String tenantId, String organizationId, String recipientId);
}
