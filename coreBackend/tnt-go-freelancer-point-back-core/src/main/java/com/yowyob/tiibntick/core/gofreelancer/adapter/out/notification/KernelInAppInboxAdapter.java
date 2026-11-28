package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.InAppNotification;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification.NotificationType;
import com.yowyob.tiibntick.core.notify.application.port.in.IInAppInboxUseCase;
import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Lot C-36 on the Kernel inbox (decision D-157): the Go fields of a notification (type, event key, link, render
 * parameters) travel as metadata of the Kernel message and are read back from it.
 */
@Component
@RequiredArgsConstructor
public class KernelInAppInboxAdapter implements IInAppInboxPort {

    private final IInAppInboxUseCase inbox;

    @Override
    public Mono<Void> send(UUID recipientUserId, String title, String message, Map<String, String> metadata) {
        return inbox.send(recipientUserId, APPLICATION, title, message, metadata).then();
    }

    @Override
    public Flux<InAppNotification> mine() {
        return inbox.mine(APPLICATION).map(KernelInAppInboxAdapter::toNotification);
    }

    @Override
    public Mono<InAppNotification> markRead(UUID notificationId) {
        return inbox.markRead(notificationId).map(KernelInAppInboxAdapter::toNotification);
    }

    static InAppNotification toNotification(InAppMessage m) {
        return new InAppNotification(m.id(), m.recipientUserId(), type(m.meta(META_TYPE)), m.title(), m.body(),
                m.meta(META_PARAMS), m.meta(META_LINK_KIND), uuid(m.meta(META_LINK_ID)), m.createdAt(), m.readAt());
    }

    private static NotificationType type(String value) {
        if (value == null) return null;
        try {
            return NotificationType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
