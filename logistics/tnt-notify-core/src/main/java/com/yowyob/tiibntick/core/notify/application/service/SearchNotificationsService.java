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
 * Application service for querying the notification history.
 *
 * @author MANFOUO Braun
 */
public class SearchNotificationsService implements ISearchNotificationsUseCase {

    private final ISearchNotificationsPort searchPort;

    public SearchNotificationsService(ISearchNotificationsPort searchPort) {
        this.searchPort = searchPort;
    }

    @Override
    public Flux<Notification> findByRecipientId(String recipientId) {
        return searchPort.findByRecipientId(recipientId);
    }

    @Override
    public Flux<Notification> findByStatus(DeliveryStatus status) {
        return searchPort.findByStatus(status);
    }

    @Override
    public Mono<Notification> findById(String notificationId) {
        return searchPort.findById(notificationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + notificationId)));
    }

    @Override
    public Flux<Notification> findPendingByRecipient(String recipientId) {
        return searchPort.findPendingByRecipient(recipientId);
    }
}
