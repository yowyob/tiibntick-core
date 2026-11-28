package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationDeliveryDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SendNotificationRequestDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The in-app inbox on the Kernel notification engine: channel {@code IN_APP} (the persistent inbox, not the live
 * {@code WEBSOCKET} one), read through {@code GET /api/notifications/inbox} and marked through
 * {@code POST /api/notifications/inbox/{id}/read} (decision D-157).
 */
public class KernelInAppInboxAdapter implements IInAppInboxPort {

    static final String CHANNEL = "IN_APP";

    private final KernelNotificationClient client;

    public KernelInAppInboxAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Mono<InAppMessage> send(UUID recipientUserId, String application, String title, String body,
                                   Map<String, String> metadata) {
        return client.sendInApp(new SendNotificationRequestDto(recipientUserId, null, CHANNEL, null, title, body,
                        null, metadata == null || metadata.isEmpty() ? null : metadata, application))
                .map(KernelInAppInboxAdapter::toMessage);
    }

    @Override
    public Flux<InAppMessage> mine(String application) {
        return client.inbox(application)
                // the inbox lists the caller's in-app deliveries; anything else is not an inbox message
                .filter(d -> d.channel() == null || CHANNEL.equals(d.channel()))
                .map(KernelInAppInboxAdapter::toMessage);
    }

    @Override
    public Mono<InAppMessage> markRead(UUID messageId) {
        return client.markInboxRead(messageId).map(KernelInAppInboxAdapter::toMessage);
    }

    static InAppMessage toMessage(NotificationDeliveryDto d) {
        Map<String, String> meta = new LinkedHashMap<>();
        if (d.metadata() != null) {
            d.metadata().forEach((k, v) -> {
                if (k != null && v != null) meta.put(k, String.valueOf(v));
            });
        }
        return new InAppMessage(d.id(), d.recipientUserId(), d.subject(), d.body(), meta,
                d.requestedAt() != null ? d.requestedAt() : d.sentAt(), d.readAt());
    }
}
