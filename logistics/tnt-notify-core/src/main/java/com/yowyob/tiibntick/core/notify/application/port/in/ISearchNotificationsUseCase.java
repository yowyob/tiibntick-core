package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port for querying notification history.
 * Used by TiiBnTick Link, Go, and Agency dashboards.
 *
 * @author MANFOUO Braun
 */
public interface ISearchNotificationsUseCase {

    /**
     * Returns all notifications sent to a given user.
     */
    Flux<Notification> findByRecipientId(String recipientId);

    /**
     * Returns notifications filtered by status (e.g., FAILED for retry
     * dashboards).
     */
    Flux<Notification> findByStatus(DeliveryStatus status);

    /**
     * Returns a single notification by ID.
     */
    Mono<Notification> findById(String notificationId);

    /**
     * Returns unread / pending notifications for a user (PENDING).
     */
    Flux<Notification> findPendingByRecipient(String recipientId);
}
