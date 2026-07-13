package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.linkback.application.port.in.QueryLinkNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Thin orchestration over {@link ISearchNotificationsUseCase} — the single
 * Link-facing entry point the BFF calls instead of reaching tnt-notify-core directly.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class QueryLinkNotificationsApplicationService implements QueryLinkNotificationsUseCase {

    private final ISearchNotificationsUseCase searchNotificationsUseCase;

    @Override
    public Flux<Notification> forRecipient(String recipientId) {
        return searchNotificationsUseCase.findByRecipientId(recipientId);
    }

    @Override
    public Flux<Notification> pendingForRecipient(String recipientId) {
        return searchNotificationsUseCase.findPendingByRecipient(recipientId);
    }
}
