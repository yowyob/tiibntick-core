package com.yowyob.tiibntick.bootstrap.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies idle-poll cost settings to every Kafka consumer factory in the reactor.
 *
 * <h2>Why a BeanPostProcessor rather than application.yml</h2>
 *
 * <p>All 22 modules that own consumers build their own
 * {@link DefaultKafkaConsumerFactory} from a hardcoded map and none of them reads
 * {@code KafkaProperties}. Only {@code spring.kafka.bootstrap-servers} (all 22) and
 * {@code spring.kafka.consumer.group-id} (eight of them) reach the consumers, and
 * only through {@code @Value}. Everything else declared under
 * {@code spring.kafka.consumer.*} is inert, so neither yaml nor an environment
 * variable can tune a single consumer. This post-processor is the one place that
 * reaches all of them without editing 22 files spread across the modules.
 *
 * <h2>What it costs to do nothing</h2>
 *
 * <p>Measured in production on 27/11/2026: 86 consumers (59 {@code @KafkaListener}
 * methods, 13 factories at concurrency 2), each polling with Kafka's default
 * {@code fetch.max.wait.ms} of 500 ms and heart-beating every 3 s. That is roughly
 * 200 round trips a second with no traffic at all, and it cost 0.30 to 0.45 of a
 * core permanently — making this service the heaviest CPU consumer on the host
 * while idle, twenty times the kernel next door.
 *
 * <p>Raising {@code fetch.max.wait.ms} costs nothing in latency:
 * {@code fetch.min.bytes} is 1, so the broker answers as soon as one byte is
 * available. The wait only applies to fetches that would have returned nothing.
 * Likewise {@code session.timeout.ms} is untouched, so failure detection is
 * unchanged by a longer heartbeat interval.
 *
 * <h2>What it will not do</h2>
 *
 * <p>It never overwrites a value a module declared for itself — each key is applied
 * only when absent from that factory's own map. Concurrency is left alone unless
 * {@code tnt.kafka.consumer.concurrency} is set to a positive number, because the
 * modules chose 2 deliberately; the property exists so the choice can be revisited
 * from the outside instead of in 13 files.
 */
@Component
public class TntKafkaConsumerTuning implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TntKafkaConsumerTuning.class);

    /** Kafka's own default when a factory declares no {@code session.timeout.ms}. */
    private static final int KAFKA_DEFAULT_SESSION_TIMEOUT_MS = 45_000;

    private final int fetchMaxWaitMs;
    private final int heartbeatIntervalMs;
    private final int concurrency;
    private final AtomicBoolean announced = new AtomicBoolean();

    public TntKafkaConsumerTuning(
            @Value("${tnt.kafka.consumer.fetch-max-wait-ms:5000}") int fetchMaxWaitMs,
            @Value("${tnt.kafka.consumer.heartbeat-interval-ms:10000}") int heartbeatIntervalMs,
            @Value("${tnt.kafka.consumer.concurrency:0}") int concurrency) {

        if (fetchMaxWaitMs < 1) {
            throw new IllegalArgumentException(
                    "tnt.kafka.consumer.fetch-max-wait-ms must be positive, got " + fetchMaxWaitMs);
        }
        if (heartbeatIntervalMs < 1) {
            throw new IllegalArgumentException(
                    "tnt.kafka.consumer.heartbeat-interval-ms must be positive, got " + heartbeatIntervalMs);
        }
        // Kafka rejects a consumer whose heartbeat is not comfortably below its session
        // timeout, with an error that says nothing about which setting is at fault.
        // Failing here names the property instead.
        if (heartbeatIntervalMs >= KAFKA_DEFAULT_SESSION_TIMEOUT_MS / 3) {
            throw new IllegalArgumentException(
                    "tnt.kafka.consumer.heartbeat-interval-ms must stay below a third of "
                            + "session.timeout.ms (" + KAFKA_DEFAULT_SESSION_TIMEOUT_MS + " ms), got "
                            + heartbeatIntervalMs);
        }
        this.fetchMaxWaitMs = fetchMaxWaitMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.concurrency = concurrency;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DefaultKafkaConsumerFactory<?, ?> consumerFactory) {
            tuneConsumerFactory(consumerFactory, beanName);
        } else if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> containerFactory) {
            tuneContainerFactory(containerFactory, beanName);
        }
        return bean;
    }

    private void tuneConsumerFactory(DefaultKafkaConsumerFactory<?, ?> factory, String beanName) {
        Map<String, Object> declared = factory.getConfigurationProperties();
        Map<String, Object> toApply = new LinkedHashMap<>();

        if (!declared.containsKey(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG)) {
            toApply.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        }
        if (!declared.containsKey(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG)
                && heartbeatFitsSessionTimeout(declared, beanName)) {
            toApply.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        }

        if (!toApply.isEmpty()) {
            factory.updateConfigs(toApply);
            announceOnce();
            log.debug("Kafka consumer factory '{}' tuned: {}", beanName, toApply);
        }
    }

    /**
     * A module may declare a shorter {@code session.timeout.ms} than our heartbeat
     * interval would allow. Kafka would then refuse the consumer, so leave that
     * factory on its own heartbeat and say why.
     */
    private boolean heartbeatFitsSessionTimeout(Map<String, Object> declared, String beanName) {
        Object sessionTimeout = declared.get(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG);
        if (sessionTimeout == null) {
            return true;
        }
        int sessionTimeoutMs;
        try {
            sessionTimeoutMs = Integer.parseInt(String.valueOf(sessionTimeout));
        } catch (NumberFormatException unparseable) {
            log.warn("Kafka consumer factory '{}' declares an unreadable session.timeout.ms ({}); "
                    + "leaving its heartbeat interval alone", beanName, sessionTimeout);
            return false;
        }
        if (heartbeatIntervalMs >= sessionTimeoutMs / 3) {
            log.warn("Kafka consumer factory '{}' declares session.timeout.ms={} ms, too short for a "
                            + "{} ms heartbeat; leaving its heartbeat interval alone",
                    beanName, sessionTimeoutMs, heartbeatIntervalMs);
            return false;
        }
        return true;
    }

    private void tuneContainerFactory(ConcurrentKafkaListenerContainerFactory<?, ?> factory, String beanName) {
        if (concurrency > 0) {
            factory.setConcurrency(concurrency);
            announceOnce();
            log.debug("Kafka listener container factory '{}' set to concurrency {}", beanName, concurrency);
        }

        // Spring polls with ContainerProperties.pollTimeout (5 s by default). A fetch
        // wait longer than that would be cut short by the poll returning empty, which
        // throws the saving away without any benefit. Nothing in this repository sets
        // pollTimeout, so aligning it here changes no deliberate choice.
        long pollTimeoutMs = factory.getContainerProperties().getPollTimeout();
        if (fetchMaxWaitMs > pollTimeoutMs) {
            factory.getContainerProperties().setPollTimeout(fetchMaxWaitMs);
            announceOnce();
            log.debug("Kafka listener container factory '{}' pollTimeout raised {} ms -> {} ms to match "
                    + "fetch.max.wait.ms", beanName, pollTimeoutMs, fetchMaxWaitMs);
        }
    }

    private void announceOnce() {
        if (announced.compareAndSet(false, true)) {
            log.info("Kafka consumer tuning active: fetch.max.wait.ms={} ms, heartbeat.interval.ms={} ms, "
                            + "concurrency={}",
                    fetchMaxWaitMs, heartbeatIntervalMs,
                    concurrency > 0 ? String.valueOf(concurrency) : "left to each module");
        }
    }
}
