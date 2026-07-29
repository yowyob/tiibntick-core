package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage real-time notification streams for delivery persons.
 * Uses Sinks.Many to multicast events to connected clients.
 */
@Service
@Slf4j
public class NotificationStreamService {

    private final Map<UUID, Sinks.Many<MatchingNotificationEvent>> userSinks = new ConcurrentHashMap<>();

    /**
     * Retrieves or creates a stream for a specific delivery person.
     *
     * @param freelancerId The ID of the delivery person.
     * @return A Flux of MatchingNotificationEvents.
     */
    public Flux<MatchingNotificationEvent> getNotificationStream(UUID freelancerId) {
        log.info("Client connected to notification stream: {}", freelancerId);
        return userSinks.computeIfAbsent(freelancerId, id -> Sinks.many().multicast().onBackpressureBuffer())
                .asFlux();
    }

    /**
     * Pushes a notification to the delivery person's stream if they are connected.
     *
     * @param event The notification event.
     */
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
