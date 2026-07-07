package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import reactor.core.publisher.Mono;

/**
 * Secondary port for publishing notification domain events to the event bus
 * (Kafka).
 * Consumed by tnt-realtime-core for in-app WebSocket push and audit logging.
 *
 * @author MANFOUO Braun
 */
public interface IPublishNotificationEventPort {

    /**
     * Publishes a notification-sent event to the Kafka topic
     * "tnt.notifications.sent".
     *
     * @param notification the successfully sent notification
     */
    Mono<Void> publishNotificationSent(Notification notification);

    /**
     * Publishes a notification-failed event to "tnt.notifications.failed".
     *
     * @param notification the failed notification
     */
    Mono<Void> publishNotificationFailed(Notification notification);
}
