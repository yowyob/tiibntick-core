package com.yowyob.kernel.event.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot test configuration that activates the {@code yow-event-kernel}
 * autoconfiguration in a minimal test application context.
 *
 * <p>A {@link SimpleMeterRegistry} is registered so that {@code MicrometerEventMetrics}
 * can be wired without requiring a full Prometheus or Grafana setup, and a
 * {@code tntKafkaTemplate}-qualified {@link KafkaTemplate} is registered so that
 * {@code KafkaEventPublisher} can be wired — in the real application this bean is
 * provided by {@code tnt-bootstrap}'s {@code TntKafkaConfig}, which is obviously not
 * present when testing this module in isolation.
 *
 * <p>Used exclusively in {@link YowEventKernelIntegrationTest}. The infrastructure
 * (PostgreSQL, Kafka, Redis) is provided by Testcontainers via
 * {@link org.springframework.test.context.DynamicPropertySource}.
 */
@Configuration
@EnableAutoConfiguration
@Import(YowEventKernelAutoConfiguration.class)
public class YowEventKernelTestConfig {

    /**
     * Registers a simple in-memory {@link MeterRegistry} for use in tests.
     * Replaces the Prometheus registry that would normally be configured in production.
     */
    @Bean
    public MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Stands in for {@code tnt-bootstrap}'s {@code @Primary tntObjectMapper} bean,
     * required by qualifier name by {@code OutboxEntryMapper} (and any other
     * module-level mapper depending on it).
     */
    @Bean(name = "tntObjectMapper")
    public ObjectMapper tntObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Stands in for {@code tnt-bootstrap}'s {@code tntKafkaTemplate} bean, which
     * {@code KafkaEventPublisher} requires by qualifier name.
     */
    @Bean(name = "tntKafkaTemplate")
    public KafkaTemplate<String, String> tntKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") final String bootstrapServers) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configs);
        return new KafkaTemplate<>(producerFactory);
    }
}
