package com.yowyob.tiibntick.core.gofreelancer.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/** String/String listener factory (manual ack) for gofp consumers of other modules' JSON events. */
@Configuration
public class GofpKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean("gofpKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> gofpKafkaListenerContainerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Factory for the GOFP listeners whose method parameter is a JSON event POJO
     * ({@code SubscriptionAttemptEvent}, {@code MatchingNotificationEvent}) or a raw JSON {@code String}
     * ({@code MatchingUseCase}).
     *
     * <p>Those listeners used to name no factory, so they ran on Spring Boot's default one
     * ({@code ByteArrayDeserializer}, no message converter) and every record of
     * {@code gofp.subscription.attempts} / {@code gofp.matching.notifications} failed with
     * {@code MessageConversionException: Cannot convert from [byte[]] to [...Event]}. Here records are read as
     * {@code String} and converted to the declared parameter type by the platform's Jackson mapper (Java-time
     * aware, so {@code Instant} fields work). Offsets are committed by the container after the listener returns
     * (default {@code BATCH} mode, no {@code Acknowledgment} parameter needed).
     */
    @Bean("gofpJsonKafkaListenerContainerFactory")
    @SuppressWarnings("deprecation")
    public ConcurrentKafkaListenerContainerFactory<String, String> gofpJsonKafkaListenerContainerFactory(
            @org.springframework.beans.factory.annotation.Qualifier("tntObjectMapper")
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setRecordMessageConverter(
                new org.springframework.kafka.support.converter.StringJsonMessageConverter(objectMapper));
        return factory;
    }
}
