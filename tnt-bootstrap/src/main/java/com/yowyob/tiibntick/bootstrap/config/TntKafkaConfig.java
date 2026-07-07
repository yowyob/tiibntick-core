package com.yowyob.tiibntick.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Kafka producer template and ObjectMapper for modules that do not define their own.
 *
 * <p>Bean names: {@code tntKafkaTemplate}, {@code tntObjectMapper}.
 * Modules without a dedicated KafkaTemplate/ObjectMapper should inject
 * these beans via {@code @Qualifier}.
 *
 * <p>Also provides fallback beans for module-specific qualifiers
 * (e.g. {@code deliveryKafkaProducer}) so that module components loaded
 * from pre-built JARs always resolve their dependencies, even when the
 * module's own {@code @Configuration} is not yet re-installed.
 *
 * @author MANFOUO Braun
 */
@Configuration
public class TntKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean("tntKafkaProducerFactory")
    public ProducerFactory<String, String> tntKafkaProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("tntKafkaTemplate")
    public KafkaTemplate<String, String> tntKafkaTemplate() {
        return new KafkaTemplate<>(tntKafkaProducerFactory());
    }

    /**
     * Fallback bean satisfying {@code @Qualifier("deliveryKafkaProducer")} injected by
     * {@code KafkaDeliveryEventPublisher}.
     * Activated only when {@code DeliveryModuleConfig} (from {@code tnt-delivery-core}) has
     * not already registered a bean of this name — i.e. when the module JAR is out of date
     * or not yet re-installed in the local Maven repository.
     */
    @Bean("deliveryKafkaProducer")
    @ConditionalOnMissingBean(name = "deliveryKafkaProducer")
    public KafkaTemplate<String, String> deliveryKafkaProducerFallback() {
        return new KafkaTemplate<>(tntKafkaProducerFactory());
    }

    /**
     * Shared reactor-kafka sender for modules using the reactive Kafka client
     * directly (e.g. {@code ProductKafkaEventPublisher}, {@code InventoryKafkaEventPublisher})
     * instead of spring-kafka's {@code KafkaTemplate}.
     */
    @Bean("tntKafkaSender")
    public KafkaSender<String, String> tntKafkaSender() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return KafkaSender.create(SenderOptions.create(props));
    }

    /**
     * Shared ObjectMapper with Java Time support — designated as {@code @Primary} so that
     * all components injecting {@code ObjectMapper} without a {@code @Qualifier} receive
     * this bean by default. Avoids {@code NoUniqueBeanDefinitionException} when multiple
     * module-specific ObjectMapper beans are also present in the context.
     */
    @Primary
    @Bean("tntObjectMapper")
    public ObjectMapper tntObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Fallback bean satisfying {@code @Qualifier("deliveryObjectMapper")} injected by
     * {@code KafkaDeliveryEventPublisher}.
     * Activated only when {@code DeliveryModuleConfig} (from {@code tnt-delivery-core}) has
     * not already registered a bean of this name.
     */
    @Bean("deliveryObjectMapper")
    @ConditionalOnMissingBean(name = "deliveryObjectMapper")
    public ObjectMapper deliveryObjectMapperFallback() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
