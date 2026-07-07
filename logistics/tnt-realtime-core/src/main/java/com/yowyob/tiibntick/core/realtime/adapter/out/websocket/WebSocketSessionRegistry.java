package com.yowyob.tiibntick.core.realtime.adapter.out.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter-level registry that holds the actual Spring {@link WebSocketSession} objects
 * and their associated outbound {@link Sinks.Many} for message delivery.
 *
 * <p>This is kept in the adapter layer (not the domain) because Spring's
 * {@link WebSocketSession} is infrastructure code, not a domain concept.
 * The domain layer works with its own {@link com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession}.</p>
 *
 * <p>This registry is local to the current JVM instance. Multi-instance broadcasting
 * is handled by Redis pub-sub in {@link RedisBackedWebSocketBroadcaster}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    /** rawSessionId → Spring WebSocketSession */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** rawSessionId → outbound message sink */
    private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    /** topic → set of rawSessionIds */
    private final Map<String, Set<String>> topicIndex = new ConcurrentHashMap<>();

    /**
     * Registers a new WebSocket session with its outbound sink.
     *
     * @param rawSessionId the Spring WebSocket session ID
     * @param session      the Spring WebSocketSession
     * @param sink         the outbound message sink
     */
    public void register(String rawSessionId, WebSocketSession session, Sinks.Many<String> sink) {
        sessions.put(rawSessionId, session);
        sinks.put(rawSessionId, sink);
        log.debug("Registered WS session in adapter registry: {}", rawSessionId);
    }

    /**
     * Removes a session from the registry and cleans up topic subscriptions.
     *
     * @param rawSessionId the Spring WebSocket session ID to remove
     */
    public void unregister(String rawSessionId) {
        sessions.remove(rawSessionId);
        sinks.remove(rawSessionId);
        topicIndex.values().forEach(subscribers -> subscribers.remove(rawSessionId));
        log.debug("Unregistered WS session from adapter registry: {}", rawSessionId);
    }

    /**
     * Indexes a session under a topic for fast broadcast lookup.
     *
     * @param rawSessionId the session ID
     * @param topic        the topic path
     */
    public void addTopicSubscription(String rawSessionId, String topic) {
        topicIndex.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(rawSessionId);
    }

    /**
     * Removes a session's subscription from a topic.
     *
     * @param rawSessionId the session ID
     * @param topic        the topic path
     */
    public void removeTopicSubscription(String rawSessionId, String topic) {
        Set<String> subscribers = topicIndex.get(topic);
        if (subscribers != null) {
            subscribers.remove(rawSessionId);
            if (subscribers.isEmpty()) {
                topicIndex.remove(topic);
            }
        }
    }

    /**
     * Returns the outbound sink for a session (for direct message push).
     *
     * @param rawSessionId the Spring WebSocket session ID
     * @return optional containing the sink, or empty if session not found
     */
    public Optional<Sinks.Many<String>> getSink(String rawSessionId) {
        return Optional.ofNullable(sinks.get(rawSessionId));
    }

    /**
     * Returns all outbound sinks for sessions subscribed to a topic.
     *
     * @param topic the topic path
     * @return map of rawSessionId → Sinks.Many for subscribers
     */
    public Map<String, Sinks.Many<String>> getSinksForTopic(String topic) {
        Set<String> subscribers = topicIndex.getOrDefault(topic, Collections.emptySet());
        Map<String, Sinks.Many<String>> result = new ConcurrentHashMap<>();
        subscribers.forEach(id -> {
            Sinks.Many<String> sink = sinks.get(id);
            if (sink != null) {
                result.put(id, sink);
            }
        });
        return result;
    }

    /**
     * Returns all currently registered outbound sinks.
     *
     * @return map of rawSessionId → Sinks.Many
     */
    public Map<String, Sinks.Many<String>> getAllSinks() {
        return Collections.unmodifiableMap(sinks);
    }

    /**
     * Pushes a message directly to a specific session by raw session ID.
     *
     * @param rawSessionId the Spring WebSocket session ID
     * @param message      the serialized message string
     */
    public void pushToSession(String rawSessionId, String message) {
        Sinks.Many<String> sink = sinks.get(rawSessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            if (result.isFailure()) {
                log.warn("Failed to push message to session {}: {}", rawSessionId, result);
            }
        }
    }

    public int getSessionCount() {
        return sessions.size();
    }
}
