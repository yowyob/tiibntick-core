package com.yowyob.tiibntick.core.billing.wallet.adapter.out.redis;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IIdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * RedisIdempotencyStore — implements IIdempotencyStore with Redis SET NX/EX.
 * Key format: wallet:idempotency:{key}
 * Lock TTL: 5 minutes (MoMo) | Result cache TTL: 24 hours.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IIdempotencyStore {

    private static final String KEY_PREFIX = "wallet:idempotency:";
    private static final String RESULT_PREFIX = "wallet:result:";

    @Qualifier("reactiveStringRedisTemplate")
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Boolean> tryAcquire(String key, Duration ttl) {
        String redisKey = KEY_PREFIX + key;
        return redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "LOCKED", ttl)
                .doOnSuccess(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        log.debug("Idempotency lock acquired: key={}", redisKey);
                    } else {
                        log.warn("Duplicate payment detected: key={}", redisKey);
                    }
                });
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(KEY_PREFIX + key);
    }

    @Override
    public Mono<Void> release(String key) {
        return redisTemplate.delete(KEY_PREFIX + key)
                .doOnSuccess(deleted -> log.debug("Idempotency lock released: key={}", key))
                .then();
    }

    @Override
    public Mono<Void> storeResult(String key, String result, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(RESULT_PREFIX + key, result, ttl)
                .then();
    }

    @Override
    public Mono<String> getResult(String key) {
        return redisTemplate.opsForValue().get(RESULT_PREFIX + key);
    }
}
