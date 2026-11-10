package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.ISearchNotificationsPort;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Notification history queries, delegated to the Kernel through {@link ISearchNotificationsPort}.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public class SearchNotificationsService implements ISearchNotificationsUseCase {

    private final ISearchNotificationsPort searchPort;

    public SearchNotificationsService(ISearchNotificationsPort searchPort) {
        this.searchPort = searchPort;
    }

    @Override
    public Flux<Notification> findByRecipientId(String tenantId, String organizationId, String recipientId) {
        return searchPort.findByRecipientId(tenantId, organizationId, recipientId);
    }

    @Override
    public Flux<Notification> findByStatus(String tenantId, String organizationId, DeliveryStatus status) {
        return searchPort.findByStatus(tenantId, organizationId, status);
    }

    @Override
    public Mono<Notification> findById(String tenantId, String organizationId, String notificationId) {
        return searchPort.findById(tenantId, organizationId, notificationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + notificationId)));
    }

    @Override
    public Flux<Notification> findPendingByRecipient(String tenantId, String organizationId, String recipientId) {
        return searchPort.findPendingByRecipient(tenantId, organizationId, recipientId);
    }
}
