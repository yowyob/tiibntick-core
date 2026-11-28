package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * <b>Outbound port</b> — the consumer-side record of events already processed, shared by every
 * Kafka consumer of the application (decided 2026-11-26: all consumers are idempotent).
 *
 * <p>Key: {@code (consumerGroup, topic, eventId)}. The consumer group scopes the record — two
 * modules consuming the same event each process it once. The topic is part of the key because
 * several producers publish one event object to two topics (a specific one and an aggregate one)
 * with the same event id, and a single group may listen to both.
 */
public interface ProcessedEventStorePort {

    /** @return {@code true} if this group already processed this event of this topic */
    Mono<Boolean> isProcessed(String consumerGroup, String topic, String eventId);

    /** Records the event as processed (idempotent: a second mark is a no-op). */
    Mono<Void> markProcessed(String consumerGroup, String topic, String eventId);

    /** Deletes the records older than {@code retention}; emits the number deleted. */
    Mono<Long> purgeOlderThan(Duration retention);
}
