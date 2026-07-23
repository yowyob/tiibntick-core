package com.yowyob.tiibntick.core.marketback.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for tnt-market-back-core.
 *
 * <p>Imported by {@code TntCoreConfig} in tnt-bootstrap. Enables component
 * scanning for this module's services/controllers/adapters and imports
 * {@link MarketBackR2dbcConfig} for the module's dedicated {@code tnt_market}
 * schema connection factory.
 *
 * @author MANFOUO Braun
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.marketback")
@Import(MarketBackR2dbcConfig.class)
public class MarketBackCoreConfig {

    /**
     * Dedicated consumer factory for tnt-market-back-core Kafka listeners, using an
     * explicit {@link StringDeserializer} for both key and value.
     *
     * <p>Fixes Audit n°5 · P-02: {@code MarketKafkaConsumer}'s 2 listeners previously had
     * no {@code containerFactory}, so they fell back to Spring Boot's autoconfigured
     * {@code kafkaListenerContainerFactory}, whose value-deserializer is
     * {@code ByteArrayDeserializer} (application.yml) — incompatible with their
     * {@code String} payload signatures.
     */
    @Bean("marketKafkaConsumerFactory")
    public ConsumerFactory<String, String> marketKafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:tnt-market-group}") String groupId) {

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
     * {@code containerFactory = "marketKafkaListenerContainerFactory"} on
     * tnt-market-back-core's {@code @KafkaListener} methods.
     */
    @Bean("marketKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> marketKafkaListenerContainerFactory(
            ConsumerFactory<String, String> marketKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(marketKafkaConsumerFactory);
        factory.setConcurrency(2);
        return factory;
    }
}
