package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import reactor.core.publisher.Mono;
import java.time.Duration;

/**
 * Secondary port — idempotency store backed by Redis.
 * Prevents double-charges for concurrent or retried payment requests.
 * Key format: {invoiceId}:{channel}
 *
 * @author MANFOUO Braun
 */
public interface IIdempotencyStore {

    /**
     * Acquires an idempotency lock for the given key.
     * Returns true if this is the FIRST acquisition (caller may proceed).
     * Returns false if the key already exists (duplicate request — skip processing).
     *
     * @param key idempotency key ({invoiceId}:{channel})
     * @param ttl lock time-to-live (default 5 minutes for MoMo)
     * @return true if first-time, false if duplicate
     */
    Mono<Boolean> tryAcquire(String key, Duration ttl);

    /**
     * Checks if a key exists in the store without modifying it.
     *
     * @param key idempotency key
     * @return true if the key exists (duplicate)
     */
    Mono<Boolean> exists(String key);

    /**
     * Releases the lock for the given key.
     * Called after a payment has been fully processed (success or failure).
     *
     * @param key idempotency key
     * @return void
     */
    Mono<Void> release(String key);

    /**
     * Stores a resolved idempotency key with a long TTL for audit purposes.
     * Prevents re-processing even after the short lock expires.
     *
     * @param key    idempotency key
     * @param result serialized result to cache (e.g. paymentIntentId)
     * @param ttl    long-term TTL (e.g. 24 hours)
     */
    Mono<Void> storeResult(String key, String result, Duration ttl);

    /**
     * Retrieves the cached result for a given key (if any).
     *
     * @param key idempotency key
     * @return cached result or empty
     */
    Mono<String> getResult(String key);
}
