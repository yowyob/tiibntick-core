package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Outbound port for real-time notification streaming (SSE).
 */
public interface NotificationStreamPort {

    Flux<MatchingNotificationEvent> getNotificationStream(UUID freelancerId);

    void pushNotification(MatchingNotificationEvent event);
}
