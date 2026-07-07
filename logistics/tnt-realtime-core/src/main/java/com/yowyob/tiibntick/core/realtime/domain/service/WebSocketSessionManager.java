package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.domain.exception.SessionNotFoundException;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Domain service managing the in-memory registry of active WebSocket sessions
 * and their topic subscriptions.
 *
 * <p>This is a per-instance (non-distributed) registry. For broadcasting to
 * sessions on other instances, the adapter layer uses Redis pub-sub.</p>
 *
 * <p>Thread-safety: all internal data structures are {@link ConcurrentHashMap}
 * instances, safe for concurrent access from reactive scheduler threads.</p>
 *
 * @author MANFOUO Braun
 */
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** sessionId → WebSocketSession (domain object) */
    private final Map<String, WebSocketSession> sessionRegistry = new ConcurrentHashMap<>();

    /** topic path → set of sessionIds subscribed */
    private final Map<String, Set<String>> topicSubscriptions = new ConcurrentHashMap<>();

    /** userId → set of sessionIds (one user may have multiple tabs/devices) */
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * Registers a new session in the in-memory registry.
     *
     * @param session the domain WebSocket session to register
     */
    public void register(WebSocketSession session) {
        sessionRegistry.put(session.getId().value(), session);
        userSessions.computeIfAbsent(session.getUserId(), k -> ConcurrentHashMap.newKeySet())
                    .add(session.getId().value());
        log.debug("Registered WebSocket session {} for user {} (tenant {})",
                session.getId(), session.getUserId(), session.getTenantId());
    }

    /**
     * Removes a session from the registry and cleans up all topic subscriptions.
     *
     * @param sessionId the session to remove
     */
    public void unregister(SessionId sessionId) {
        WebSocketSession session = sessionRegistry.remove(sessionId.value());
        if (session != null) {
            session.getSubscriptions().forEach(topic ->
                    topicSubscriptions.getOrDefault(topic, ConcurrentHashMap.newKeySet())
                                      .remove(sessionId.value()));
            Set<String> userSessionSet = userSessions.get(session.getUserId());
            if (userSessionSet != null) {
                userSessionSet.remove(sessionId.value());
                if (userSessionSet.isEmpty()) {
                    userSessions.remove(session.getUserId());
                }
            }
            log.debug("Unregistered WebSocket session {} for user {}", sessionId, session.getUserId());
        }
    }

    /**
     * Subscribes a session to a topic. Creates the topic entry if absent.
     *
     * @param sessionId the subscribing session
     * @param topic     the STOMP topic path
     * @throws SessionNotFoundException if the session does not exist
     */
    public void subscribe(SessionId sessionId, String topic) {
        WebSocketSession session = getSessionOrThrow(sessionId);
        session.subscribe(topic);
        topicSubscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
                          .add(sessionId.value());
        log.debug("Session {} subscribed to topic {}", sessionId, topic);
    }

    /**
     * Unsubscribes a session from a topic.
     *
     * @param sessionId the session to unsubscribe
     * @param topic     the STOMP topic path
     */
    public void unsubscribe(SessionId sessionId, String topic) {
        WebSocketSession session = sessionRegistry.get(sessionId.value());
        if (session != null) {
            session.unsubscribe(topic);
        }
        Set<String> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.remove(sessionId.value());
            if (subscribers.isEmpty()) {
                topicSubscriptions.remove(topic);
            }
        }
    }

    /**
     * Returns all session IDs currently subscribed to a given topic.
     *
     * @param topic the STOMP topic path
     * @return immutable snapshot of subscribed session IDs
     */
    public Set<String> getSessionIdsByTopic(String topic) {
        Set<String> subscribers = topicSubscriptions.get(topic);
        if (subscribers == null) return Collections.emptySet();
        return Set.copyOf(subscribers);
    }

    /**
     * Returns all sessions subscribed to a given topic.
     *
     * @param topic the STOMP topic path
     * @return list of active sessions for that topic
     */
    public List<WebSocketSession> getSessionsByTopic(String topic) {
        return getSessionIdsByTopic(topic).stream()
                .map(id -> sessionRegistry.get(id))
                .filter(s -> s != null && s.isActive())
                .collect(Collectors.toList());
    }

    /**
     * Returns all sessions for a specific user (across all devices/tabs).
     *
     * @param userId the user identifier
     * @return list of active sessions for that user
     */
    public List<WebSocketSession> getSessionsByUser(String userId) {
        Set<String> ids = userSessions.getOrDefault(userId, Collections.emptySet());
        return ids.stream()
                .map(id -> sessionRegistry.get(id))
                .filter(s -> s != null && s.isActive())
                .collect(Collectors.toList());
    }

    /**
     * Returns the session by its ID, if present.
     *
     * @param sessionId the session identifier
     * @return optional containing the session, or empty if not found
     */
    public Optional<WebSocketSession> findSession(SessionId sessionId) {
        return Optional.ofNullable(sessionRegistry.get(sessionId.value()));
    }

    /**
     * Updates a session's heartbeat timestamp and reactivates it if idle.
     *
     * @param sessionId the session to touch
     */
    public void touch(SessionId sessionId) {
        WebSocketSession session = sessionRegistry.get(sessionId.value());
        if (session != null) {
            session.touch();
        }
    }

    /**
     * Expires all sessions that have not received a heartbeat within the
     * specified maximum silence duration. Typically scheduled every 30 seconds.
     *
     * @param maxSilence the maximum allowed silence duration
     * @return number of sessions expired
     */
    public int expireStale(Duration maxSilence) {
        LocalDateTime now = LocalDateTime.now();
        List<SessionId> toExpire = sessionRegistry.values().stream()
                .filter(s -> !s.isAlive(now, maxSilence))
                .filter(s -> s.getStatus() != SessionStatus.EXPIRED && s.getStatus() != SessionStatus.DISCONNECTED)
                .map(WebSocketSession::getId)
                .collect(Collectors.toList());

        toExpire.forEach(id -> {
            WebSocketSession session = sessionRegistry.get(id.value());
            if (session != null) {
                session.markExpired();
            }
            unregister(id);
        });

        if (!toExpire.isEmpty()) {
            log.info("Expired {} stale WebSocket sessions", toExpire.size());
        }
        return toExpire.size();
    }

    /**
     * Returns the total count of currently active sessions across all tenants.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return (int) sessionRegistry.values().stream().filter(WebSocketSession::isActive).count();
    }

    /**
     * Returns the count of sessions subscribed to a specific topic.
     *
     * @param topic the STOMP topic path
     * @return subscriber count
     */
    public int getSubscriberCount(String topic) {
        return topicSubscriptions.getOrDefault(topic, Collections.emptySet()).size();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private WebSocketSession getSessionOrThrow(SessionId sessionId) {
        WebSocketSession session = sessionRegistry.get(sessionId.value());
        if (session == null) {
            throw new SessionNotFoundException(sessionId.value());
        }
        return session;
    }
}
