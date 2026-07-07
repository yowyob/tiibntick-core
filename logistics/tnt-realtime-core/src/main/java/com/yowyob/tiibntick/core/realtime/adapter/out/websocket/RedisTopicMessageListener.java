package com.yowyob.tiibntick.core.realtime.adapter.out.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;

/**
 * Subscribes to Redis pub-sub channels matching {@code tnt:rt:topic:*}
 * and forwards received messages to local WebSocket sessions.
 *
 * <p>When a broadcast is published to Redis by any JVM instance, all instances
 * receive the message here and push it to their locally-connected sessions that
 * are subscribed to the corresponding STOMP topic.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class RedisTopicMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisTopicMessageListener.class);
    private static final String PATTERN = "tnt:rt:topic:*";

    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final WebSocketSessionRegistry sessionRegistry;

    public RedisTopicMessageListener(ReactiveRedisMessageListenerContainer listenerContainer,
                                     WebSocketSessionRegistry sessionRegistry) {
        this.listenerContainer = listenerContainer;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Starts listening to Redis pub-sub channels on application startup.
     * Routes messages from Redis channels to local WebSocket session sinks.
     */
    @PostConstruct
    public void startListening() {
        Flux<ReactiveSubscription.PatternMessage<String, String, String>> messageFlux =
                listenerContainer.receive(PatternTopic.of(PATTERN));

        messageFlux
                .doOnNext(msg -> routeToLocalSessions(msg.getChannel(), msg.getMessage()))
                .doOnError(ex -> log.error("Redis message listener error: {}", ex.getMessage(), ex))
                .onErrorResume(ex -> Flux.empty())
                .subscribe();

        log.info("Redis pub-sub listener started — pattern: {}", PATTERN);
    }

    /**
     * Routes a Redis message to all local WebSocket sessions subscribed to the topic.
     *
     * @param redisChannel the Redis channel name (e.g. tnt:rt:topic:delivery:MISSION-123)
     * @param wireMessage  the wire-format message (topicPath|jsonBody)
     */
    private void routeToLocalSessions(String redisChannel, String wireMessage) {
        // Convert Redis channel back to STOMP topic path
        // Channel format: tnt:rt:topic:delivery:MISSION-123
        // Topic format:   /topic/delivery/MISSION-123
        String topicPath = redisChannel.replace("tnt:rt", "").replace(":", "/");

        int separatorIdx = wireMessage.indexOf('|');
        if (separatorIdx < 0) {
            log.warn("Malformed wire message from Redis channel {}: no separator", redisChannel);
            return;
        }
        String messageBody = wireMessage.substring(separatorIdx + 1);

        sessionRegistry.getSinksForTopic(topicPath).forEach((sessionId, sink) -> {
            reactor.core.publisher.Sinks.EmitResult result = sink.tryEmitNext(messageBody);
            if (result.isFailure()) {
                log.warn("Failed to push Redis message to session {}: {}", sessionId, result);
            }
        });

        log.trace("Routed Redis message to topic {}", topicPath);
    }
}
