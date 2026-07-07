package com.yowyob.kernel.event.adapter.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.out.EventIdempotencyStorePort;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed implementation of {@link EventIdempotencyStorePort}.
 *
 * <p>Uses Spring Data Redis' reactive {@link ReactiveStringRedisTemplate} to
 * perform atomic {@code SET NX EX} operations, ensuring that concurrently
 * arriving duplicate events are detected and discarded without a race condition.
 *
 * <p>Key format: {@code yow:event:idempotency:{correlationId}}
 *
 * <p>The TTL should be set to at least the maximum expected end-to-end processing
 * latency of a consumer, multiplied by a safety factor of 3. The default
 * configuration in {@code application.yml} is 24 hours.
 */
@Component
public class RedisEventIdempotencyStore implements EventIdempotencyStorePort {

    private static final String KEY_PREFIX = "yow:event:idempotency:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisEventIdempotencyStore(@Qualifier("reactiveStringRedisTemplate") final ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
    }

    @Override
    public Mono<Boolean> isAlreadyProcessed(final String correlationId) {
        return redisTemplate.hasKey(KEY_PREFIX + correlationId);
    }

    @Override
    public Mono<Void> markAsProcessed(final String correlationId, final Duration ttl) {
        return redisTemplate.opsForValue()
            .set(KEY_PREFIX + correlationId, "1", ttl)
            .then();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses Redis {@code SET NX PX} (set-if-not-exists with millisecond expiry)
     * to ensure atomicity. Returns {@code true} if this is the first time the
     * correlation ID is seen (i.e. the SET succeeded).
     */
    @Override
    public Mono<Boolean> checkAndMark(final String correlationId, final Duration ttl) {
        return redisTemplate.opsForValue()
            .setIfAbsent(KEY_PREFIX + correlationId, "1", ttl);
    }

    @Override
    public Mono<Void> clear(final String correlationId) {
        return redisTemplate.delete(KEY_PREFIX + correlationId).then();
    }
}
