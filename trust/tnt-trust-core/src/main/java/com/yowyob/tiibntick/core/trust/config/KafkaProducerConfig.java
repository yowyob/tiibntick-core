package com.yowyob.tiibntick.core.trust.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration — {@code KafkaProducerConfig}.
 *
 * <p>Configures the Kafka producer beans for {@code tnt-trust}.
 * The producer sends {@link com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent}
 * messages to the {@code yow.trust.events} topic.
 *
 * <h3>Reliability Settings</h3>
 * <ul>
 *   <li>Idempotent producer — prevents duplicate messages on retry</li>
 *   <li>{@code acks=all} — strongest durability guarantee</li>
 *   <li>{@code retries=3} — handles transient broker failures</li>
 *   <li>Linger + batch size optimized for low-volume trust events</li>
 * </ul>
 *
 * <p>This configuration is skipped ({@link ConditionalOnMissingBean}) when
 * {@code tnt-bootstrap} already provides a {@code KafkaTemplate} bean.
 * In standalone testing mode, it provides its own producer.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Kafka producer factory for string key-value messages.
     * Uses an idempotent producer to prevent duplicate Kafka messages
     * during Fabric submission retries.
     */
    @Bean("tntTrustProducerFactory")
    @ConditionalOnMissingBean(name = "tntTrustProducerFactory")
    public ProducerFactory<String, String> tntTrustProducerFactory() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Idempotent producer — prevents duplicate messages on broker retry
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Strongest durability: all in-sync replicas must acknowledge
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retry on transient broker failures
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Low linger — trust events should be sent promptly
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Moderate batch size — trust events are small JSON payloads
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // Compression for high-throughput periods
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate for sending string messages to Kafka.
     * The template is used by {@link com.yowyob.tiibntick.core.trust.adapter.out.messaging.KafkaTrustEventPublisherAdapter}.
     */
    @Bean("tntTrustKafkaTemplate")
    @ConditionalOnMissingBean(name = "tntTrustKafkaTemplate")
    public KafkaTemplate<String, String> tntTrustKafkaTemplate() {
        return new KafkaTemplate<>(tntTrustProducerFactory());
    }

    /**
     * Kafka consumer factory for {@code yow.trust.events.committed}, consumed by
     * {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     * Named (not the ambiguous {@code ConsumerFactory<String,String>} type) so
     * {@link TntTrustAutoConfiguration#tntTrustKafkaListenerContainerFactory} can
     * reference it directly instead of relying on Spring to disambiguate among
     * the dozen other modules' own consumer factories on the classpath.
     */
    @Bean("tntTrustConsumerFactory")
    @ConditionalOnMissingBean(name = "tntTrustConsumerFactory")
    public ConsumerFactory<String, String> tntTrustConsumerFactory() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-trust-core");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
