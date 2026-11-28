package com.yowyob.kernel.event.adapter.kafka.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Common retry and dead-letter handling of every Kafka listener of the application — decision [G.8] of
 * 2026-11-26, lot L0 ({@code cible-evenements-colis-livraison.md} §3.10 point 1).
 *
 * <p>Installs on every {@link AbstractKafkaListenerContainerFactory} bean (each module's and Spring Boot's
 * default one) a {@link DefaultErrorHandler} that:
 * <ol>
 *   <li>retries a record whose listener threw, with an exponential backoff (1 s, ×2), up to
 *       {@value #MAX_ATTEMPTS_PROPERTY} attempts in total ({@value #DEFAULT_MAX_ATTEMPTS} by default);</li>
 *   <li>then publishes it to the common dead-letter topic {@value #DLQ_TOPIC} through a
 *       {@link DeadLetterPublishingRecoverer}: same key and value, the original headers plus the
 *       {@code kafka_dlt-*} headers (original topic, partition, offset, consumer group, exception);
 *       an {@code ERROR KAFKA_RECORD_DEAD_LETTERED} log and the counter
 *       {@code yow.event.consumer.dead_lettered.total{topic,group}} make it visible.</li>
 * </ol>
 * The {@link IdempotentRecordInterceptor} installed by {@link IdempotentListenerContainerFactoryPostProcessor}
 * is not touched: a failed attempt is never marked processed, a retried one is filtered again.
 *
 * <p>Only a listener that <b>waits</b> for its work ({@code ReactiveListenerSupport}, tnt-common-core) lets
 * the error reach this handler; fire-and-forget listeners ({@code .subscribe()}) are frozen debt of the
 * ArchUnit rule {@code TntArchitectureTest}, fixed by lots L7/L8.
 *
 * <p>A factory that already carries its own error handler (tnt-sync-core and tnt-link-back-core, with their
 * module dead-letter topics) is left as it is: those move to {@value #DLQ_TOPIC} in lot L8.
 */
public class TntKafkaErrorHandlingPostProcessor implements BeanPostProcessor, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TntKafkaErrorHandlingPostProcessor.class);

    /** Common dead-letter topic ({@code TntTopics.DLQ} — this L0 module cannot see the L1 constant). */
    public static final String DLQ_TOPIC = "tnt.dlq";
    /** Total delivery attempts of a failing record before it is dead-lettered. */
    public static final String MAX_ATTEMPTS_PROPERTY = "tnt.kafka.retry.max-attempts";
    public static final int DEFAULT_MAX_ATTEMPTS = 4;
    /** Backoff of spec §3.10: {@code ExponentialBackOff(1000, 2.0)}. */
    static final long INITIAL_INTERVAL_MS = 1_000L;
    static final double MULTIPLIER = 2.0;

    private static final Field COMMON_ERROR_HANDLER = commonErrorHandlerField();

    private final int maxAttempts;
    private final Supplier<KafkaOperations<Object, Object>> templateFactory;
    private final ObjectProvider<MeterRegistry> meterRegistry;
    private volatile KafkaOperations<Object, Object> template;
    private volatile DeadLetterPublishingRecoverer deadLetterRecoverer;

    /**
     * Production constructor: the dead-letter producer is created lazily from
     * {@code spring.kafka.bootstrap-servers} (a post-processor must not depend on other beans).
     */
    public TntKafkaErrorHandlingPostProcessor(final Environment environment,
                                              final ObjectProvider<MeterRegistry> meterRegistry) {
        this(environment.getProperty(MAX_ATTEMPTS_PROPERTY, Integer.class, DEFAULT_MAX_ATTEMPTS),
            () -> deadLetterTemplate(environment.getProperty("spring.kafka.bootstrap-servers", "localhost:9092")),
            meterRegistry);
    }

    TntKafkaErrorHandlingPostProcessor(final int maxAttempts,
                                       final Supplier<KafkaOperations<Object, Object>> templateFactory,
                                       final ObjectProvider<MeterRegistry> meterRegistry) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(MAX_ATTEMPTS_PROPERTY + " must be >= 1, got " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
        this.templateFactory = Objects.requireNonNull(templateFactory);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
            CommonErrorHandler existing = errorHandlerOf(factory);
            if (existing != null) {
                log.info("Kafka listener container factory '{}' keeps its own error handler ({}); common "
                    + "retry + {} not installed", beanName, existing.getClass().getSimpleName(), DLQ_TOPIC);
                return bean;
            }
            factory.setCommonErrorHandler(newErrorHandler());
            log.debug("Retry ({} attempts) + dead-letter {} installed on Kafka listener container factory '{}'",
                maxAttempts, DLQ_TOPIC, beanName);
        }
        return bean;
    }

    /** @return a new error handler (one per factory) sharing the dead-letter producer */
    DefaultErrorHandler newErrorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxAttempts - 1);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        return new DefaultErrorHandler(recoverer(), backOff);
    }

    int maxAttempts() {
        return maxAttempts;
    }

    /** Logs, counts and publishes the exhausted record to {@value #DLQ_TOPIC}. */
    ConsumerAwareRecordRecoverer recoverer() {
        return (record, consumer, exception) -> {
            String group = IdempotentRecordInterceptor.groupId(consumer);
            log.error("KAFKA_RECORD_DEAD_LETTERED topic={} partition={} offset={} group={} key={} eventId={} "
                    + "after {} attempts: {}", record.topic(), record.partition(), record.offset(), group,
                record.key(), KafkaEventIds.resolve(record), maxAttempts, String.valueOf(exception));
            MeterRegistry registry = meterRegistry.getIfAvailable();
            if (registry != null) {
                Counter.builder("yow.event.consumer.dead_lettered.total")
                    .description("Kafka records published to " + DLQ_TOPIC + " after exhausting their retries")
                    .tag("topic", record.topic()).tag("group", group)
                    .register(registry).increment();
            }
            deadLetterRecoverer().accept(record, consumer, exception);
        };
    }

    private DeadLetterPublishingRecoverer deadLetterRecoverer() {
        DeadLetterPublishingRecoverer recoverer = deadLetterRecoverer;
        if (recoverer == null) {
            synchronized (this) {
                recoverer = deadLetterRecoverer;
                if (recoverer == null) {
                    template = templateFactory.get();
                    recoverer = new DeadLetterPublishingRecoverer(template,
                        (ConsumerRecord<?, ?> record, Exception e) -> new TopicPartition(DLQ_TOPIC, -1));
                    deadLetterRecoverer = recoverer;
                }
            }
        }
        return recoverer;
    }

    @Override
    public void destroy() {
        if (template instanceof KafkaTemplate<Object, Object> kafkaTemplate
                && kafkaTemplate.getProducerFactory() instanceof DefaultKafkaProducerFactory<Object, Object> factory) {
            factory.destroy();
        }
    }

    /** @return the error handler a factory was configured with, {@code null} if none */
    static CommonErrorHandler errorHandlerOf(final AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
        return (CommonErrorHandler) ReflectionUtils.getField(COMMON_ERROR_HANDLER, factory);
    }

    private static Field commonErrorHandlerField() {
        // spring-kafka exposes a setter only; reading the field is the only way not to override a module's own
        // handler. Failing fast here makes a spring-kafka upgrade that renames it impossible to miss.
        Field field = ReflectionUtils.findField(AbstractKafkaListenerContainerFactory.class, "commonErrorHandler",
            CommonErrorHandler.class);
        if (field == null) {
            throw new IllegalStateException("AbstractKafkaListenerContainerFactory.commonErrorHandler not found: "
                + "check TntKafkaErrorHandlingPostProcessor against the spring-kafka version");
        }
        ReflectionUtils.makeAccessible(field);
        return field;
    }

    /** Producer of the dead-letter topic: accepts any key/value type a container may have deserialised. */
    static KafkaTemplate<Object, Object> deadLetterTemplate(final String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "tnt-dlq-producer");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props,
            new DeadLetterValueSerializer(), new DeadLetterValueSerializer()));
    }
}
