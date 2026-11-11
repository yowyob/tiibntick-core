package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NotificationStreamPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.MatchingNotification;
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

    private final Map<UUID, Sinks.Many<MatchingNotification>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Flux<MatchingNotification> getNotificationStream(UUID freelancerId) {
        log.info("Client connected to notification stream: {}", freelancerId);
        // directBestEffort: the sink outlives its subscribers. The former buffered multicast sink cancelled itself
        // when the last client left, and every later connection of the same freelancer then completed at once
        // (an SSE that opens and closes immediately, retried in a loop by the browser).
        return userSinks.computeIfAbsent(freelancerId, id -> Sinks.many().multicast().<MatchingNotification>directBestEffort())
                .asFlux();
    }

    @Override
    public void pushNotification(MatchingNotification event) {
        Sinks.Many<MatchingNotification> sink = userSinks.get(event.freelancerId());
        if (sink != null) {
            log.info("Pushing real-time notification to client: {}", event.freelancerId());
            // Pushes can come from several consumer threads: retry only the "concurrent emission" failure.
            Sinks.EmitResult result = sink.tryEmitNext(event);
            for (int i = 0; result == Sinks.EmitResult.FAIL_NON_SERIALIZED && i < 100; i++) {
                result = sink.tryEmitNext(event);
            }
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.warn("Notification for {} not delivered to the stream: {}", event.freelancerId(), result);
            }
        } else {
            log.debug("No active stream for delivery person: {}", event.freelancerId());
        }
    }
}
