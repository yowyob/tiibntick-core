package com.yowyob.tiibntick.core.realtime.adapter.out.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.domain.model.BroadcastTopic;
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

/**
 * Regression test for the Chantier G discovery that {@link IWebSocketBroadcaster#subscribeToTopic}
 * (via the interface's default {@code Flux.empty()} fallback) never actually delivered anything —
 * {@code WatchSubDeliverersApplicationService}'s fleet stream and, now, the Link tile SSE endpoint
 * both depend on {@link RedisBackedWebSocketBroadcaster#subscribeToTopic} actually forwarding
 * messages published on the corresponding Redis channel.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class RedisBackedWebSocketBroadcasterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    private RedisBackedWebSocketBroadcaster broadcaster;
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        String host = REDIS_CONTAINER.getHost();
        int port = REDIS_CONTAINER.getMappedPort(6379);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        ReactiveRedisMessageListenerContainer listenerContainer =
                new ReactiveRedisMessageListenerContainer((ReactiveRedisConnectionFactory) connectionFactory);

        broadcaster = new RedisBackedWebSocketBroadcaster(
                new WebSocketSessionRegistry(), redisTemplate, listenerContainer,
                new ObjectMapper(), new SimpleMeterRegistry());
    }

    /**
     * The Lettuce pub-sub connection behind {@code listenerContainer.receive(...)} isn't live the
     * instant {@code subscribeToTopic()} returns its Flux — Redis pub-sub has no backlog, so a
     * publish before the SUBSCRIBE round-trip completes is silently lost. Rather than guess a
     * fixed delay (flaky under load), republish on an interval until the subscriber (via
     * {@code take(1)}) has received one and cancels this Flux.
     *
     * <p>Bounded to 30 republishes (3s) so a leftover subscription from one test can't leak into
     * and contaminate the next (each test uses its own {@link BeforeEach}-scoped broadcaster, but
     * they all publish through the same Testcontainers Redis instance).
     */
    private static reactor.core.Disposable publishUntilReceived(RedisBackedWebSocketBroadcaster broadcaster,
                                                                  BroadcastTopic topic, String payload) {
        return Flux.interval(Duration.ofMillis(100))
                .take(30)
                .flatMap(tick -> broadcaster.broadcastRaw(topic, payload))
                .subscribe();
    }

    @Test
    @DisplayName("subscribeToTopic() emits messages broadcast on the topic's Redis channel")
    void subscribeToTopicEmitsBroadcastMessages() {
        BroadcastTopic topic = BroadcastTopic.forTile("u4pruy0");

        StepVerifier.create(
                broadcaster.subscribeToTopic(topic.path())
                        .take(1)
                        .doOnSubscribe(s -> publishUntilReceived(broadcaster, topic, "{\"nodeId\":\"abc\"}"))
        )
        .expectNext("{\"nodeId\":\"abc\"}")
        .expectComplete()
        .verify(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("subscribeToTopic() only emits messages for its own topic, not other tiles")
    void subscribeToTopicIsScopedToItsOwnChannel() {
        BroadcastTopic subscribed = BroadcastTopic.forTile("u4pruy1");
        BroadcastTopic other = BroadcastTopic.forTile("gbsuv1");

        StepVerifier.create(
                broadcaster.subscribeToTopic(subscribed.path())
                        .take(1)
                        .doOnSubscribe(s -> {
                            publishUntilReceived(broadcaster, other, "{\"nodeId\":\"wrong-tile\"}");
                            publishUntilReceived(broadcaster, subscribed, "{\"nodeId\":\"right-tile\"}");
                        })
        )
        .expectNext("{\"nodeId\":\"right-tile\"}")
        .expectComplete()
        .verify(Duration.ofSeconds(20));
    }

    /**
     * Regression test for the second bug found while building the above tests:
     * {@code broadcastToTopic}/{@code broadcastRawToTopic} (the String-topicPath convenience API
     * used by {@code MissionStatusEventConsumer} and {@code GpsPingApplicationService}'s fleet
     * broadcast) built a Redis channel name that {@link RedisTopicMessageListener} could not decode
     * back to the right topic path — so cross-instance delivery for every caller of
     * {@code broadcastToTopic} was silently broken. Proves {@code broadcastToTopic} and
     * {@code subscribeToTopic} now agree on the same channel.
     */
    @Test
    @DisplayName("broadcastToTopic() and subscribeToTopic() agree on the same Redis channel")
    void broadcastToTopicIsReceivedBySubscribeToTopic() {
        String topicPath = BroadcastTopic.forTile("u4pruy2").path();
        record Payload(String nodeId) {
        }

        StepVerifier.create(
                broadcaster.subscribeToTopic(topicPath)
                        .take(1)
                        .doOnSubscribe(s -> Flux.interval(Duration.ofMillis(100))
                                .take(30)
                                .flatMap(tick -> broadcaster.broadcastToTopic(topicPath, new Payload("fleet-ping")))
                                .subscribe())
        )
        .expectNext("{\"nodeId\":\"fleet-ping\"}")
        .expectComplete()
        .verify(Duration.ofSeconds(20));
    }
}
