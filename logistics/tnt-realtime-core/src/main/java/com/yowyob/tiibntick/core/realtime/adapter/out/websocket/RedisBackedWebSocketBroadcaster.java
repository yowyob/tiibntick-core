package com.yowyob.tiibntick.core.realtime.adapter.out.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Outbound adapter implementing {@link IWebSocketBroadcaster} using a
 * two-phase broadcast strategy:
 *
 * <ol>
 *   <li><b>Local push:</b> directly push to WebSocket sessions on this JVM instance
 *       via their {@link reactor.core.publisher.Sinks.Many} outbound sinks.</li>
 *   <li><b>Redis pub-sub:</b> publish the message to the corresponding Redis channel
 *       ({@code tnt:rt:topic:{topicPath}}) so other JVM instances can forward it
 *       to their local sessions.</li>
 * </ol>
 *
 * <p>The Redis subscriber ({@link RedisTopicMessageListener}) handles the
 * cross-instance forwarding on the receiving side.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class RedisBackedWebSocketBroadcaster implements IWebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisBackedWebSocketBroadcaster.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter broadcastCounter;

    public RedisBackedWebSocketBroadcaster(WebSocketSessionRegistry sessionRegistry,
                                           @Qualifier("realtimeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper,
                                           MeterRegistry meterRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        this.broadcastCounter = Counter.builder("tnt.realtime.broadcasts.total")
                .description("Total WebSocket broadcasts issued")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> broadcast(BroadcastTopic topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return broadcastRaw(topic, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast payload for topic {}: {}", topic, e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> broadcastRaw(BroadcastTopic topic, String json) {
        broadcastCounter.increment();

        // Build the wire message: "TOPIC_PATH|JSON_BODY"
        // The delimiter allows receivers to extract the topic and route to correct sessions
        String wireMessage = topic.path() + "|" + json;

        // Phase 1: local push to this instance's subscribed sessions
        pushToLocalSessions(topic.path(), wireMessage);

        // Phase 2: publish to Redis for other instances
        return redisTemplate.convertAndSend(topic.toRedisChannel(), wireMessage)
                .doOnNext(count -> log.trace("Published to Redis channel {} — {} subscribers", topic.toRedisChannel(), count))
                .doOnError(ex -> log.warn("Redis publish failed for topic {}: {}", topic, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    /**
     * Internal method to broadcast a pre-serialized JSON string to a topic path (String form).
     * This variant accepts the topic path as a String and converts it to Redis channel format.
     *
     * @param topicPath the STOMP topic path string (e.g., /topic/fleet/FRL-ORG-001)
     * @param json the pre-serialized JSON string
     * @return Mono completing when broadcast is dispatched
     */
    private Mono<Void> broadcastRawToTopic(String topicPath, String json) {
        // Build the wire message: "TOPIC_PATH|JSON_BODY"
        String wireMessage = topicPath + "|" + json;

        // Phase 1: local push to this instance's subscribed sessions
        pushToLocalSessions(topicPath, wireMessage);

        // Phase 2: publish to Redis for other instances
        String redisChannel = "tnt:rt:topic:" + topicPath;
        return redisTemplate.convertAndSend(redisChannel, wireMessage)
                .doOnNext(count -> log.trace("Published to Redis channel {} — {} subscribers", redisChannel, count))
                .doOnError(ex -> log.warn("Redis publish failed for topic {}: {}", topicPath, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    @Override
    public Mono<Void> broadcastToTopic(String topicPath, Object payload) {
        broadcastCounter.increment();

        try {
            String json = objectMapper.writeValueAsString(payload);
            return broadcastRawToTopic(topicPath, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast payload for topic {}: {}", topicPath, e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * Pushes a wire message directly to all local sessions subscribed to the topic.
     * This covers sessions on this JVM instance without going through Redis round-trip.
     *
     * @param topicPath   the STOMP topic path
     * @param wireMessage the wire-format message string
     */
    private void pushToLocalSessions(String topicPath, String wireMessage) {
        sessionRegistry.getSinksForTopic(topicPath).forEach((sessionId, sink) -> {
            Sinks.EmitResult result = sink.tryEmitNext(wireMessage);
            if (result.isFailure()) {
                log.warn("Local push failed to session {}: {}", sessionId, result);
            }
        });
    }
}
