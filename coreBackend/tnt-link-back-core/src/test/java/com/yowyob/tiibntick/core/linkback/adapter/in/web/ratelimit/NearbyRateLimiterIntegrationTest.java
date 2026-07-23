package com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Phase 0 stop-gap per-user throttle on Link's {@code /nearby}
 * endpoints (audit n6 S25, Chantier G — see docs/audits/remediation/phase-0-critical.md).
 *
 * <p>Verifies against a real Redis instance that repeated calls beyond the configured
 * threshold are denied ({@code tryAcquire()} flips to {@code false}) within the same
 * fixed window, and that a different user (or a different endpoint bucket) is unaffected.
 *
 * @author Dilane PAFE
 */
@Testcontainers
@Tag("integration")
class NearbyRateLimiterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private NearbyRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        String host = REDIS_CONTAINER.getHost();
        int port = REDIS_CONTAINER.getMappedPort(6379);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();

        ReactiveStringRedisTemplate redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        rateLimiter = new NearbyRateLimiter(redisTemplate, MAX_REQUESTS, WINDOW_SECONDS);

        ((ReactiveRedisConnectionFactory) connectionFactory).getReactiveConnection()
                .serverCommands()
                .flushAll()
                .block();
    }

    @Test
    @DisplayName("allows exactly MAX_REQUESTS calls then throttles the rest within the same window")
    void throttlesAfterThreshold() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        java.util.List<Boolean> results = Flux.range(0, MAX_REQUESTS + 3)
                .concatMap(i -> rateLimiter.tryAcquire(tenantId, userId, "nodes"))
                .collectList()
                .block();

        assertThat(results).hasSize(MAX_REQUESTS + 3);
        long allowedCount = results.stream().filter(Boolean::booleanValue).count();
        long deniedCount = results.stream().filter(allowed -> !allowed).count();

        assertThat(allowedCount).isEqualTo(MAX_REQUESTS);
        assertThat(deniedCount).isEqualTo(3);
        // The first MAX_REQUESTS calls must be the ones allowed, in order (fixed window counter).
        assertThat(results.subList(0, MAX_REQUESTS)).containsOnly(true);
        assertThat(results.subList(MAX_REQUESTS, results.size())).containsOnly(false);
    }

    @Test
    @DisplayName("throttling is scoped per user — a different user gets its own budget")
    void perUserIsolation() {
        UUID tenantId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            StepVerifier.create(rateLimiter.tryAcquire(tenantId, userA, "nodes"))
                    .expectNext(true)
                    .verifyComplete();
        }
        // userA is now exhausted...
        StepVerifier.create(rateLimiter.tryAcquire(tenantId, userA, "nodes"))
                .expectNext(false)
                .verifyComplete();
        // ...but userB, same tenant/endpoint, is untouched.
        StepVerifier.create(rateLimiter.tryAcquire(tenantId, userB, "nodes"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("throttling is scoped per endpoint — nodes and alerts nearby don't share a bucket")
    void perEndpointIsolation() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < MAX_REQUESTS; i++) {
            StepVerifier.create(rateLimiter.tryAcquire(tenantId, userId, "nodes"))
                    .expectNext(true)
                    .verifyComplete();
        }
        StepVerifier.create(rateLimiter.tryAcquire(tenantId, userId, "nodes"))
                .expectNext(false)
                .verifyComplete();
        // Different endpoint bucket ("alerts") for the same user is still fresh.
        StepVerifier.create(rateLimiter.tryAcquire(tenantId, userId, "alerts"))
                .expectNext(true)
                .verifyComplete();
    }
}
