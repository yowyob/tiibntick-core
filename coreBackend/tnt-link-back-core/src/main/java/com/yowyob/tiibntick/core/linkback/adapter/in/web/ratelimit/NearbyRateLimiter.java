package com.yowyob.tiibntick.core.linkback.adapter.in.web.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-user, fixed-window throttle for the Link {@code /nearby} endpoints
 * ({@code NetworkNodeController}, {@code NetworkAlertController}).
 *
 * <p><b>Phase 0 stop-gap</b> (audit n6 S25, Chantier G — see
 * {@code docs/audits/remediation/phase-0-critical.md}): these endpoints had no
 * per-user throttling, making them exploitable for scraping/light DoS in
 * combination with the unbounded result set (now capped separately at the
 * repository query level via {@code MAX_NEARBY_RESULTS}). This is a temporary
 * mitigation, not the target design — the Phase 1 redesign replaces {@code /nearby}
 * entirely with geohash tiles served through a dedicated real-time BFF.
 *
 * <p>Implementation: a plain Redis {@code INCR}/{@code EXPIRE} fixed-window counter,
 * keyed per tenant+user+endpoint+window. Reuses the {@code reactiveStringRedisTemplate}
 * bean that Spring Boot autoconfigures from {@code spring.data.redis.*} (the same
 * bean several other modules already inject via
 * {@code @Qualifier("reactiveStringRedisTemplate")}, e.g. billing-wallet's
 * {@code RedisIdempotencyStore} and resource-core's {@code VehicleLocationRedisAdapter})
 * — no new dependency, no new library choice, same pattern already used across the repo.
 *
 * @author Dilane PAFE
 */
@Slf4j
@Component
public class NearbyRateLimiter {

    private static final String KEY_PREFIX = "link:ratelimit:nearby:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int maxRequestsPerWindow;
    private final long windowSeconds;

    public NearbyRateLimiter(
            @Qualifier("reactiveStringRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            @Value("${tnt.link-back.nearby.rate-limit.max-requests:30}") int maxRequestsPerWindow,
            @Value("${tnt.link-back.nearby.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSeconds = windowSeconds;
    }

    /**
     * @param tenantId caller's tenant
     * @param userId   caller's user id — the throttle key
     * @param endpoint short discriminator so nodes/alerts nearby endpoints don't share a bucket
     * @return {@code true} if this call is within the allowed rate, {@code false} if it must be rejected (HTTP 429)
     */
    public Mono<Boolean> tryAcquire(UUID tenantId, UUID userId, String endpoint) {
        long window = Instant.now().getEpochSecond() / windowSeconds;
        String key = KEY_PREFIX + endpoint + ":" + tenantId + ":" + userId + ":" + window;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> ensureExpiry = count == 1L
                            ? redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                            : Mono.just(Boolean.TRUE);
                    return ensureExpiry.thenReturn(count <= maxRequestsPerWindow);
                })
                .doOnNext(allowed -> {
                    if (!allowed) {
                        log.warn("Rate limit exceeded for {} — tenant={} user={}", endpoint, tenantId, userId);
                    }
                });
    }
}
