package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.MatchingNotification;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Outbound port for real-time notification streaming (SSE).
 */
public interface NotificationStreamPort {

    Flux<MatchingNotification> getNotificationStream(UUID freelancerId);

    void pushNotification(MatchingNotification notification);
}
