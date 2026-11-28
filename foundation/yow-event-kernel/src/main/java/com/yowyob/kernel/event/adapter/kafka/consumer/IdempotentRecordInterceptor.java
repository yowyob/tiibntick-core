package com.yowyob.kernel.event.adapter.kafka.consumer;

import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.kernel.event.config.YowEventKernelProperties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.KafkaUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * Duplicate filter installed on <b>every</b> Kafka listener container of the application by
 * {@link IdempotentListenerContainerFactoryPostProcessor} — no listener has to opt in.
 *
 * <ul>
 *   <li>{@link #intercept}: a record whose event ({@link KafkaEventIds#resolve}) was already
 *       processed by this consumer group on this topic is skipped (the listener is not invoked;
 *       {@code yow.event.consumer.duplicate.total} is incremented);</li>
 *   <li>{@link #success}: the event is marked processed only <b>after</b> the listener returned
 *       normally — a listener that throws leaves it unmarked, so the container's redelivery
 *       processes it again.</li>
 * </ul>
 *
 * <p>Fail-open: when the store cannot be reached within {@code lookup-timeout}, the record is
 * processed (at-least-once wins over exactly-once). Explicit replays ({@code X-Yow-Replay: true},
 * sent by the manual replay/DLQ use cases) are never skipped, consistently with
 * {@code ReplayEventService} which also clears its own idempotency record before replaying.
 *
 * <p>Limit: for a listener that hands its work to a reactive pipeline with {@code subscribe()}
 * and returns, "success" means the synchronous hand-off succeeded — the same moment the container
 * commits the offset, so the filter never marks an event Kafka would redeliver.
 */
public class IdempotentRecordInterceptor implements RecordInterceptor<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(IdempotentRecordInterceptor.class);

    private final ObjectProvider<ProcessedEventStorePort> store;
    private final ObjectProvider<EventMetricsPort> metrics;
    private final ObjectProvider<YowEventKernelProperties> properties;

    /** Set when {@link #intercept} skipped the current record: the container still calls success(). */
    private final ThreadLocal<Boolean> skipped = new ThreadLocal<>();

    public IdempotentRecordInterceptor(
            final ObjectProvider<ProcessedEventStorePort> store,
            final ObjectProvider<EventMetricsPort> metrics,
            final ObjectProvider<YowEventKernelProperties> properties) {
        this.store = Objects.requireNonNull(store);
        this.metrics = Objects.requireNonNull(metrics);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(final ConsumerRecord<Object, Object> record,
                                                    final Consumer<Object, Object> consumer) {
        skipped.remove();
        if (!applies(record) || KafkaEventIds.isReplay(record)) {
            return record;
        }
        String group = groupId(consumer);
        String eventId = KafkaEventIds.resolve(record);
        try {
            Boolean processed = store.getObject().isProcessed(group, record.topic(), eventId)
                .block(lookupTimeout());
            if (Boolean.TRUE.equals(processed)) {
                log.info("Duplicate skipped: group={} topic={} eventId={} partition={} offset={}",
                    group, record.topic(), eventId, record.partition(), record.offset());
                EventMetricsPort m = metrics.getIfAvailable();
                if (m != null) {
                    m.recordDuplicateSkipped(group, record.topic());
                }
                skipped.set(Boolean.TRUE);
                return null;
            }
        } catch (RuntimeException e) {
            log.warn("Duplicate lookup failed (record processed anyway): group={} topic={} eventId={}: {}",
                group, record.topic(), eventId, e.toString());
        }
        return record;
    }

    @Override
    public void success(final ConsumerRecord<Object, Object> record, final Consumer<Object, Object> consumer) {
        if (Boolean.TRUE.equals(skipped.get())) {
            skipped.remove();
            return;
        }
        if (!applies(record)) {
            return;
        }
        String group = groupId(consumer);
        String eventId = KafkaEventIds.resolve(record);
        try {
            store.getObject().markProcessed(group, record.topic(), eventId).block(lookupTimeout());
        } catch (RuntimeException e) {
            log.warn("Could not mark event processed (a redelivery would be processed again): group={} topic={} "
                + "eventId={}: {}", group, record.topic(), eventId, e.toString());
        }
    }

    @Override
    public void afterRecord(final ConsumerRecord<Object, Object> record, final Consumer<Object, Object> consumer) {
        skipped.remove();
    }

    private boolean applies(final ConsumerRecord<Object, Object> record) {
        YowEventKernelProperties props = properties.getIfAvailable();
        YowEventKernelProperties.ConsumerIdempotency config = props != null ? props.getConsumerIdempotency() : null;
        if (config != null && (!config.isEnabled() || config.getExcludedTopics().contains(record.topic()))) {
            return false;
        }
        return record != null;
    }

    private Duration lookupTimeout() {
        YowEventKernelProperties props = properties.getIfAvailable();
        return props != null ? props.getConsumerIdempotency().getLookupTimeout() : Duration.ofSeconds(5);
    }

    static String groupId(final Consumer<?, ?> consumer) {
        String group = KafkaUtils.getConsumerGroupId();
        if (group == null && consumer != null) {
            try {
                group = consumer.groupMetadata().groupId();
            } catch (RuntimeException ignored) {
                // consumer without group metadata
            }
        }
        return group != null ? group : "no-group";
    }
}
