package com.yowyob.tiibntick.core.realtime.adapter.in.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.adapter.out.websocket.RedisBackedWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.adapter.out.websocket.WebSocketSessionRegistry;
import com.yowyob.tiibntick.core.realtime.application.service.NotificationBroadcastApplicationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-user notification stream, end to end on a real Redis: what
 * {@code broadcastToUser} publishes reaches the SSE stream of that user only.
 */
@Testcontainers
@Tag("integration")
@DisplayName("Per-user SSE notification stream on Redis")
class UserNotificationStreamIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    private NotificationBroadcastApplicationService service;
    private UserNotificationSseController controller;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        RedisBackedWebSocketBroadcaster broadcaster = new RedisBackedWebSocketBroadcaster(
                new WebSocketSessionRegistry(), new ReactiveStringRedisTemplate(factory),
                new ReactiveRedisMessageListenerContainer((ReactiveRedisConnectionFactory) factory),
                new ObjectMapper(), new SimpleMeterRegistry());
        service = new NotificationBroadcastApplicationService(broadcaster);
        controller = new UserNotificationSseController(service);
    }

    private static TntUserIdentity identity(UUID userId) {
        return new TntUserIdentity(userId, UUID.randomUUID(), UUID.randomUUID(), null, null, Set.of(), false);
    }

    /** Redis pub-sub keeps no backlog: republish until the subscriber (which cancels via take) got one. */
    private reactor.core.Disposable publishRepeatedly(String userId, Map<String, Object> payload) {
        return Flux.interval(Duration.ofMillis(100)).take(40)
                .flatMap(tick -> service.broadcastToUser(userId, payload)).subscribe();
    }

    @Test
    @DisplayName("a notification broadcast to a user reaches that user's stream as a 'notification' event")
    void subscriptionAndReception() {
        UUID user = UUID.randomUUID();
        StepVerifier.create(controller.notificationsOfCurrentUser(identity(user))
                        .doOnSubscribe(s -> publishRepeatedly(user.toString(), Map.of("title", "Offre", "n", 1)))
                        .filter(ev -> ev.event() != null)
                        .take(1))
                .assertNext(ev -> {
                    assertThat(ev.event()).isEqualTo("notification");
                    assertThat(ev.data()).contains("\"title\":\"Offre\"");
                })
                .expectComplete()
                .verify(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("a notification of another user never reaches the stream")
    void anotherUsersNotificationIsNotDelivered() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        StepVerifier.create(controller.notificationsOfCurrentUser(identity(me))
                        .doOnSubscribe(s -> publishRepeatedly(other.toString(), Map.of("title", "secret")))
                        .filter(ev -> ev.event() != null)
                        .take(Duration.ofSeconds(2)))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }
}
