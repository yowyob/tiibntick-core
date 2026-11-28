package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastNotificationUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IStreamUserNotificationsUseCase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double of the realtime per-user notification transport: what is broadcast to a user is emitted, as JSON,
 * to the connected streams of that user (what Redis does between instances in production).
 */
public class InMemoryUserNotificationBus implements IBroadcastNotificationUseCase, IStreamUserNotificationsUseCase {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    private Sinks.Many<String> sink(String userId) {
        return sinks.computeIfAbsent(userId, id -> Sinks.many().multicast().<String>directBestEffort());
    }

    @Override
    public Mono<Void> broadcastToTopic(String topic, Object payload) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> broadcastToUser(String userId, Object payload) {
        return Mono.fromRunnable(() -> {
            try {
                sink(userId).tryEmitNext(mapper.writeValueAsString(payload));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public Flux<String> streamNotificationsOf(String userId) {
        return sink(userId).asFlux();
    }
}
