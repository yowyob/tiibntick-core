package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Flux;

/**
 * Resolves notifications for the current Link user. Reuses tnt-notify-core's
 * {@code Notification} aggregate directly — no notification storage/dispatch
 * logic is duplicated here.
 */
public interface QueryLinkNotificationsUseCase {

    Flux<Notification> forRecipient(String recipientId);

    Flux<Notification> pendingForRecipient(String recipientId);
}
