package com.yowyob.tiibntick.core.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for the tnt-delivery-core module.
 * Wires infrastructure beans: Kafka producer, ObjectMapper with Java Time support.
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = {
        "com.yowyob.tiibntick.delivery",
        "com.yowyob.tiibntick.core.delivery"
})
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.delivery.adapter.out.persistence.repository",
        "com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository"
})
public class DeliveryModuleConfig {

    /**
     * Jackson ObjectMapper with Java Time module registered.
     * Declared as ConditionalOnMissingBean to avoid conflict with a global bean.
     */
    @Bean
    @ConditionalOnMissingBean(name = "deliveryObjectMapper") //ObjectMapper.class)
    public ObjectMapper deliveryObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Kafka consumer factory for delivery domain event consumers.
     * Used by {@code FreelancerVehicleEventConsumer} and similar listeners.
     */
    @Bean("deliveryConsumerFactory")
    @ConditionalOnMissingBean(name = "deliveryConsumerFactory")
    public ConsumerFactory<String, String> deliveryConsumerFactory(Environment env) {
        String bootstrapServers = env.getProperty(
                "spring.kafka.bootstrap-servers", "localhost:9092");
        String groupId = env.getProperty(
                "spring.kafka.consumer.group-id", "tnt-delivery-core");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka listener container factory for delivery Kafka consumers.
     * Referenced by {@code @KafkaListener(containerFactory = "deliveryKafkaListenerContainerFactory")}.
     */
    @Bean("deliveryKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "deliveryKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> deliveryKafkaListenerContainerFactory(
            Environment env) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deliveryConsumerFactory(env));
        factory.setConcurrency(2);
        return factory;
    }
}
