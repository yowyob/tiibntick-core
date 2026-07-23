package com.yowyob.tiibntick.core.sales.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration entry point for tnt-sales-core.
 * Author: MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.sales")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository"
)
public class SalesCoreConfiguration {

    /**
     * Dedicated consumer factory for tnt-sales-core Kafka listeners, using an explicit
     * {@link StringDeserializer} for both key and value.
     *
     * <p>Fixes Audit n°5 · P-02: {@code DeliveryEventSalesConsumer}'s 3 listeners
     * previously had no {@code containerFactory}, so they fell back to Spring Boot's
     * autoconfigured {@code kafkaListenerContainerFactory}, whose value-deserializer is
     * {@code ByteArrayDeserializer} (application.yml) — incompatible with their
     * {@code ConsumerRecord<String, String>} signatures.
     */
    @Bean("salesKafkaConsumerFactory")
    public ConsumerFactory<String, String> salesKafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:tnt-sales-core}") String groupId) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory backing
     * {@code containerFactory = "salesKafkaListenerContainerFactory"} on
     * tnt-sales-core's {@code @KafkaListener} methods.
     */
    @Bean("salesKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> salesKafkaListenerContainerFactory(
            ConsumerFactory<String, String> salesKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(salesKafkaConsumerFactory);
        factory.setConcurrency(2);
        return factory;
    }
}
