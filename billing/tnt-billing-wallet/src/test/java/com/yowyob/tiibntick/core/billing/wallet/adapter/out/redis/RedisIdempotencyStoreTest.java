package com.yowyob.tiibntick.core.billing.wallet.adapter.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisIdempotencyStore.
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyStore Tests")
class RedisIdempotencyStoreTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisIdempotencyStore(redisTemplate);
    }

    @Test
    @DisplayName("tryAcquire returns true on first call (key not existing)")
    void tryAcquireFirstCall() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(store.tryAcquire("INV-001:MTN_MOMO", Duration.ofMinutes(5)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("tryAcquire returns false on duplicate call (key already exists)")
    void tryAcquireDuplicateCall() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(store.tryAcquire("INV-001:MTN_MOMO", Duration.ofMinutes(5)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("release deletes key from Redis")
    void releaseDeletesKey() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(store.release("INV-001:MTN_MOMO"))
                .verifyComplete();

        verify(redisTemplate).delete("wallet:idempotency:INV-001:MTN_MOMO");
    }
}
