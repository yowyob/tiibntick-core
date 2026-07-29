package com.yowyob.tiibntick.core.gofreelancer.adapter.out.cache;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis implementation of the CachePort.
 *
 * @author TiiBnTick Team
 * @date 08/07/2026
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    @Override
    public Mono<Boolean> set(String key, Object value, Duration ttl) {
        if (ttl != null) {
            return redisTemplate.opsForValue()
                    .set(key, value, ttl)
                    .doOnSuccess(result -> log.debug("Cached key: {} with TTL: {}", key, ttl))
                    .onErrorResume(e -> {
                        log.error("Failed to cache key: {}", key, e);
                        return Mono.just(false);
                    });
        } else {
            return redisTemplate.opsForValue()
                    .set(key, value)
                    .doOnSuccess(result -> log.debug("Cached key: {} (no TTL)", key))
                    .onErrorResume(e -> {
                        log.error("Failed to cache key: {}", key, e);
                        return Mono.just(false);
                    });
        }
    }

    @Override
    public <T> Mono<T> get(String key, Class<T> clazz) {
        return redisTemplate.opsForValue()
                .get(key)
                .map(value -> clazz.cast(value))
                .doOnNext(value -> log.debug("Cache hit for key: {}", key))
                .doOnTerminate(() -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache lookup completed for key: {}", key);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to get cached key: {}", key, e);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> delete(String key) {
        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(deleted -> log.debug("Deleted key: {} (success: {})", key, deleted))
                .onErrorResume(e -> {
                    log.error("Failed to delete key: {}", key, e);
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.error("Failed to check existence of key: {}", key, e);
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Long> increment(String key) {
        return redisTemplate.opsForValue()
                .increment(key)
                .doOnSuccess(value -> log.debug("Incremented key: {} to {}", key, value))
                .onErrorResume(e -> {
                    log.error("Failed to increment key: {}", key, e);
                    return Mono.just(0L);
                });
    }

    @Override
    public Mono<Boolean> expire(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl)
                .doOnSuccess(result -> log.debug("Set expiration on key: {} with TTL: {}", key, ttl))
                .onErrorResume(e -> {
                    log.error("Failed to set expiration on key: {}", key, e);
                    return Mono.just(false);
                });
    }
}
