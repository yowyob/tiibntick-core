package com.yowyob.kernel.event.adapter.persistence.repository;

import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * R2DBC implementation of {@link ProcessedEventStorePort} on {@code event_bus.processed_events}
 * (changeset 007). Times come from the database clock.
 */
@Repository
public class R2dbcProcessedEventStore implements ProcessedEventStorePort {

    private static final String EXISTS_SQL = """
        SELECT 1 FROM event_bus.processed_events
         WHERE consumer_group = :group AND topic = :topic AND event_id = :eventId
        """;

    private static final String MARK_SQL = """
        INSERT INTO event_bus.processed_events (consumer_group, topic, event_id, processed_at)
        VALUES (:group, :topic, :eventId, NOW())
        ON CONFLICT (consumer_group, topic, event_id) DO NOTHING
        """;

    private static final String PURGE_SQL = """
        DELETE FROM event_bus.processed_events
         WHERE processed_at < NOW() - make_interval(secs => :retentionSeconds)
        """;

    private final R2dbcEntityTemplate template;

    public R2dbcProcessedEventStore(final R2dbcEntityTemplate template) {
        this.template = Objects.requireNonNull(template);
    }

    @Override
    public Mono<Boolean> isProcessed(final String consumerGroup, final String topic, final String eventId) {
        return template.getDatabaseClient().sql(EXISTS_SQL)
            .bind("group", consumerGroup)
            .bind("topic", topic)
            .bind("eventId", eventId)
            .map((row, meta) -> Boolean.TRUE)
            .first()
            .defaultIfEmpty(Boolean.FALSE);
    }

    @Override
    public Mono<Void> markProcessed(final String consumerGroup, final String topic, final String eventId) {
        return template.getDatabaseClient().sql(MARK_SQL)
            .bind("group", consumerGroup)
            .bind("topic", topic)
            .bind("eventId", eventId)
            .fetch().rowsUpdated()
            .then();
    }

    @Override
    public Mono<Long> purgeOlderThan(final Duration retention) {
        return template.getDatabaseClient().sql(PURGE_SQL)
            .bind("retentionSeconds", retention.toMillis() / 1000d)
            .fetch().rowsUpdated();
    }
}
