package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.domain.exception.SessionNotFoundException;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WebSocketSessionManager}.
 * Verifies in-memory session registry, topic subscriptions, and stale expiry.
 *
 * @author MANFOUO Braun
 */
class WebSocketSessionManagerTest {

    private WebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new WebSocketSessionManager();
    }

    private WebSocketSession buildSession(String sessionId, String userId, String tenantId) {
        return WebSocketSession.builder()
                .id(SessionId.of(sessionId))
                .userId(userId)
                .tenantId(tenantId)
                .deviceType(DeviceType.ANDROID)
                .deviceInfo(DeviceInfo.of(DeviceType.ANDROID, "1.0", "Android 13"))
                .build();
    }

    @Test
    @DisplayName("register() adds session and getActiveSessionCount increments")
    void registerAddsSession() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("unregister() removes session and cleans up topic subscriptions")
    void unregisterRemovesSessionAndSubscriptions() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);
        manager.subscribe(SessionId.of("s1"), "/topic/delivery/M1");

        manager.unregister(SessionId.of("s1"));

        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
        assertThat(manager.getSessionIdsByTopic("/topic/delivery/M1")).isEmpty();
    }

    @Test
    @DisplayName("subscribe() registers topic → session mapping")
    void subscribeRegistersTopic() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);

        manager.subscribe(SessionId.of("s1"), "/topic/delivery/MISSION-001");

        assertThat(manager.getSubscriberCount("/topic/delivery/MISSION-001")).isEqualTo(1);
        assertThat(manager.getSessionsByTopic("/topic/delivery/MISSION-001")).hasSize(1);
    }

    @Test
    @DisplayName("subscribe() with invalid topic path throws IllegalArgumentException")
    void subscribeWithInvalidTopicThrows() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);

        assertThatThrownBy(() -> manager.subscribe(SessionId.of("s1"), "/wrong/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/topic/");
    }

    @Test
    @DisplayName("subscribe() with unknown session throws SessionNotFoundException")
    void subscribeWithUnknownSessionThrows() {
        assertThatThrownBy(() -> manager.subscribe(SessionId.of("ghost"), "/topic/delivery/M1"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("unsubscribe() removes session from topic set")
    void unsubscribeRemovesFromTopic() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);
        manager.subscribe(SessionId.of("s1"), "/topic/delivery/MISSION-001");

        manager.unsubscribe(SessionId.of("s1"), "/topic/delivery/MISSION-001");

        assertThat(manager.getSubscriberCount("/topic/delivery/MISSION-001")).isEqualTo(0);
    }

    @Test
    @DisplayName("getSessionsByUser() returns all sessions for a user")
    void getSessionsByUserReturnsAllSessions() {
        manager.register(buildSession("s1", "user1", "tenant-A"));
        manager.register(buildSession("s2", "user1", "tenant-A"));
        manager.register(buildSession("s3", "user2", "tenant-A"));

        List<WebSocketSession> sessions = manager.getSessionsByUser("user1");

        assertThat(sessions).hasSize(2);
        assertThat(sessions).allMatch(s -> "user1".equals(s.getUserId()));
    }

    @Test
    @DisplayName("findSession() returns Optional.empty() for unknown session")
    void findSessionReturnsEmptyForUnknown() {
        Optional<WebSocketSession> result = manager.findSession(SessionId.of("ghost"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("touch() updates heartbeat and reactivates idle session")
    void touchReactivatesIdleSession() {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);
        session.markIdle();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IDLE);

        manager.touch(SessionId.of("s1"));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("expireStale() marks sessions with no heartbeat as expired and removes them")
    void expireStaleRemovesStaleSessions() throws InterruptedException {
        WebSocketSession session = buildSession("s1", "user1", "tenant-A");
        manager.register(session);

        // Immediately expire with zero tolerance
        int expired = manager.expireStale(Duration.ZERO);

        assertThat(expired).isEqualTo(1);
        assertThat(manager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("multiple sessions on same topic each receive broadcast")
    void multipleSessionsOnSameTopic() {
        manager.register(buildSession("s1", "user1", "tenant-A"));
        manager.register(buildSession("s2", "user2", "tenant-A"));
        manager.subscribe(SessionId.of("s1"), "/topic/delivery/M1");
        manager.subscribe(SessionId.of("s2"), "/topic/delivery/M1");

        assertThat(manager.getSubscriberCount("/topic/delivery/M1")).isEqualTo(2);
        assertThat(manager.getSessionsByTopic("/topic/delivery/M1")).hasSize(2);
    }
}
