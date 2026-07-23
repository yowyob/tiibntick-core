package com.yowyob.tiibntick.core.accounting.infrastructure.config;

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
 * Auto-configuration entry point for tnt-accounting-core.
 * Enables component scanning and R2DBC repositories for the accounting module.
 * Picked up by tnt-bootstrap via META-INF/spring/autoconfigure.imports.
 * Author: MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.accounting")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository"
)
public class AccountingCoreConfiguration {

    /**
     * Dedicated consumer factory for tnt-accounting-core Kafka listeners, using an explicit
     * {@link StringDeserializer} for both key and value.
     *
     * <p>Fixes Audit n°5 · P-02: {@code BillingEventAccountingConsumer}'s 5 listeners
     * previously had no {@code containerFactory}, so they fell back to Spring Boot's
     * autoconfigured {@code kafkaListenerContainerFactory}, whose value-deserializer is
     * {@code ByteArrayDeserializer} (application.yml) — incompatible with their
     * {@code ConsumerRecord<String, String>} signatures.
     */
    @Bean("accountingKafkaConsumerFactory")
    public ConsumerFactory<String, String> accountingKafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:tnt-accounting-core}") String groupId) {

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
     * {@code containerFactory = "accountingKafkaListenerContainerFactory"} on
     * tnt-accounting-core's {@code @KafkaListener} methods.
     */
    @Bean("accountingKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> accountingKafkaListenerContainerFactory(
            ConsumerFactory<String, String> accountingKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(accountingKafkaConsumerFactory);
        factory.setConcurrency(2);
        return factory;
    }
}
