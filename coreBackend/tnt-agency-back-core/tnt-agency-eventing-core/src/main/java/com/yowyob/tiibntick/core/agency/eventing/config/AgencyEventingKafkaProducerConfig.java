package com.yowyob.tiibntick.core.agency.eventing.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer factory + {@link KafkaTemplate} for agency integration events.
 *
 * <p>Mirrors the monolith producer settings: JSON value serialization, idempotent, acks=all.
 * Exposed as a dedicated {@code agencyEventKafkaTemplate} bean so it never clashes with any
 * consumer-side or auto-configured template.
 */
@Configuration
public class AgencyEventingKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> agencyEventProducerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean(name = "agencyEventKafkaTemplate")
    public KafkaTemplate<String, Object> agencyEventKafkaTemplate(
            ProducerFactory<String, Object> agencyEventProducerFactory) {
        return new KafkaTemplate<>(agencyEventProducerFactory);
    }
}
