package com.yowyob.tiibntick.core.route.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.route")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.route.adapter.out.persistence",
        entityOperationsRef = "r2dbcEntityTemplate"
)
public class RouteCoreConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    @Bean
    @ConditionalOnMissingBean(name = "routeKafkaTemplate")
    public KafkaTemplate<String, String> routeKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");

        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(name = "routeObjectMapper")    //ObjectMapper.class)
    public ObjectMapper routeObjectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    /**
     * Reactive String Redis template for the route module.
     * Used by {@code RoadNetworkProviderAdapter} to cache the immutable
     * {@code RoadNetwork} aggregate as JSON (TTL 5 min).
     *
     * @param connectionFactory auto-configured by tnt-bootstrap
     * @return configured ReactiveStringRedisTemplate
     */
    @Bean("routeRedisTemplate")
    @ConditionalOnMissingBean(name = "routeRedisTemplate")
    public ReactiveStringRedisTemplate routeRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> ctx =
                RedisSerializationContext.<String, String>newSerializationContext(stringSerializer)
                        .key(stringSerializer)
                        .value(stringSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(stringSerializer)
                        .build();
        return new ReactiveStringRedisTemplate(connectionFactory, ctx);
    }
}