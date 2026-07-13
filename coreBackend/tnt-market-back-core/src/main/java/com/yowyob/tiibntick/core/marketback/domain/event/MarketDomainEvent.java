package com.yowyob.tiibntick.core.marketback.domain.event;

/**
 * Marker implemented by every Market domain event so {@code MarketKafkaEventPublisher}
 * can set the Kafka record key to the aggregate id — without it, {@code tnt-sync-core}'s
 * {@code EntityChangedEventConsumer} has no reliable way to recover the aggregate id from
 * a generic {@code Object} payload (each event nests it under a differently-named,
 * strongly-typed field: {@code orderId}, {@code listingId}, etc., not a flat {@code "id"}).
 *
 * @author MANFOUO Braun
 */
public interface MarketDomainEvent {
    String aggregateId();
}
