package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.NotificationStreamPort;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbound adapter: implements the NotificationStreamPort using Reactor Sinks
 * for real-time SSE streaming.
 */
@Component
@Slf4j
public class NotificationStreamAdapter implements NotificationStreamPort {

    private final Map<UUID, Sinks.Many<MatchingNotificationEvent>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Flux<MatchingNotificationEvent> getNotificationStream(UUID freelancerId) {
        log.info("Client connected to notification stream: {}", freelancerId);
        return userSinks.computeIfAbsent(freelancerId,
                id -> Sinks.many().multicast().onBackpressureBuffer())
                .asFlux();
    }

    @Override
    public void pushNotification(MatchingNotificationEvent event) {
        Sinks.Many<MatchingNotificationEvent> sink = userSinks.get(event.getFreelancerId());
        if (sink != null) {
            log.info("Pushing real-time notification to client: {}", event.getFreelancerId());
            sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
        } else {
            log.debug("No active stream for delivery person: {}", event.getFreelancerId());
        }
    }
}
