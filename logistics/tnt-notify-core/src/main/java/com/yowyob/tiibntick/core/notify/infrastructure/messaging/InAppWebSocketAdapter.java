package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * In-app notification adapter using Spring WebSocket (STOMP over SockJS).
 * Pushes notifications to connected clients subscribed to
 * /user/{userId}/queue/notifications.
 * Delegates real-time broadcasting to tnt-realtime-core via the STOMP broker.
 *
 * <p>Only registered when a {@link SimpMessagingTemplate} bean is configured
 * (i.e. {@code spring-boot-starter-websocket} + STOMP broker enabled). Without
 * it, {@link com.yowyob.tiibntick.core.notify.application.service.NotificationService}
 * simply receives one fewer {@code IMessageProviderPort} in its injected list.
 *
 * @author MANFOUO Braun
 */
@Component
@ConditionalOnBean(SimpMessagingTemplate.class)
public class InAppWebSocketAdapter implements IMessageProviderPort {

    private static final Logger log = LoggerFactory.getLogger(InAppWebSocketAdapter.class);
    private static final String DESTINATION_PREFIX = "/user/";
    private static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public InAppWebSocketAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.IN_APP_WEBSOCKET.equals(channel);
    }

    /**
     * Sends a message to the connected user's private notification queue.
     *
     * @param recipientId the actor / user ID (used as STOMP user principal)
     * @param content        the message payload
     */
    @Override
    public Mono<Void> sendMessage(NotificationChannel channel, String tenantId, String organizationId,
            String recipientId, String content) {
        log.info("Sending in-app WebSocket notification to user {}", recipientId);
        return Mono.fromRunnable(() -> messagingTemplate.convertAndSendToUser(
                recipientId,
                NOTIFICATION_QUEUE,
                content))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .doOnSuccess(v -> log.debug("WebSocket notification pushed to {}", recipientId))
                .onErrorResume(e -> {
                    log.error("WebSocket push failed for {}: {}", recipientId, e.getMessage());
                    return Mono.error(e);
                });
    }
}
