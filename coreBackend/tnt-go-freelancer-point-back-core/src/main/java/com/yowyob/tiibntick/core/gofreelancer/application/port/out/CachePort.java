package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Outbound port for caching operations.
 */
public interface CachePort {

    /**
     * Store a value in cache with optional TTL.
     *
     * @param key   cache key
     * @param value value to cache
     * @param ttl   time to live (null = no expiration)
     * @return completion signal
     */
    Mono<Boolean> set(String key, Object value, Duration ttl);

    /**
     * Retrieve a value from cache.
     *
     * @param key   cache key
     * @param clazz value class
     * @return cached value or empty
     */
    <T> Mono<T> get(String key, Class<T> clazz);

    /**
     * Delete a value from cache.
     *
     * @param key cache key
     * @return true if deleted, false if not found
     */
    Mono<Boolean> delete(String key);

    /**
     * Check if a key exists in cache.
     *
     * @param key cache key
     * @return true if exists, false otherwise
     */
    Mono<Boolean> exists(String key);

    /**
     * Increment a numeric value in cache.
     *
     * @param key cache key
     * @return incremented value
     */
    Mono<Long> increment(String key);

    /**
     * Set expiration on an existing key.
     *
     * @param key cache key
     * @param ttl time to live
     * @return true if expiration was set
     */
    Mono<Boolean> expire(String key, Duration ttl);
}
