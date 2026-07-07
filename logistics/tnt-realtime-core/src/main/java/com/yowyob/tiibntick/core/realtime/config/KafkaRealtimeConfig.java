package com.yowyob.tiibntick.core.realtime.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for tnt-realtime-core producers and consumers.
 *
 * <p>Producer: publishes realtime domain events (GPS, ETA, geofence, presence)
 * to their respective Kafka topics.</p>
 *
 * <p>Consumer: consumes ETA updates from tnt-route-core and mission status
 * events from tnt-delivery-core for WebSocket broadcast.</p>
 *
 * <p>All consumers use manual acknowledgment ({@link ContainerProperties.AckMode#MANUAL})
 * to ensure at-least-once delivery semantics.</p>
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableKafka
public class KafkaRealtimeConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:tnt-realtime-core}")
    private String consumerGroupId;

    // ─── Producer ─────────────────────────────────────────────────────────────

    /**
     * Kafka producer factory for String key / String value (JSON) messages.
     * Configured for idempotent delivery (acks=all, retries=3, idempotence=true).
     *
     * @return the producer factory
     */
    @Bean("realtimeKafkaProducerFactory")
    public ProducerFactory<String, String> realtimeKafkaProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Idempotent producer configuration
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        // Performance tuning
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768); // 32KB batches
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);       // 5ms linger

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate configured for realtime-core event publishing.
     *
     * @return the Kafka template
     */
    @Bean("realtimeKafkaTemplate")
    public KafkaTemplate<String, String> realtimeKafkaTemplate() {
        return new KafkaTemplate<>(realtimeKafkaProducerFactory());
    }

    // ─── Consumer ─────────────────────────────────────────────────────────────

    /**
     * Consumer factory for realtime-core consumers.
     * Uses {@link ErrorHandlingDeserializer} to prevent deserialization errors
     * from blocking the consumer thread.
     *
     * @return the consumer factory
     */
    @Bean("realtimeKafkaConsumerFactory")
    public ConsumerFactory<String, String> realtimeKafkaConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual acknowledgment
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        // Error-handling deserializers
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Kafka listener container factory for realtime-core consumers.
     * Uses MANUAL acknowledgment mode for at-least-once guarantees.
     *
     * @return the listener container factory
     */
    @Bean("realtimeKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> realtimeKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(realtimeKafkaConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(2); // 2 consumer threads per topic
        return factory;
    }
}
