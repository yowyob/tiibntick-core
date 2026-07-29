package com.yowyob.tiibntick.core.gofreelancer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis template configuration for the Go-Freelancer-Point module.
 *
 * <p>Does NOT redeclare {@code reactiveRedisConnectionFactory} — that bean is
 * auto-configured by Spring Boot from {@code spring.data.redis.*} properties
 * and shared across all modules. This class only registers a JSON-serialized
 * {@link ReactiveRedisTemplate} for use within this module.
 *
 * @author François-Charles ATANGA
 */
@Configuration
public class RedisConfig {

    /**
     * JSON-serialized ReactiveRedisTemplate for the gofp module.
     * Injects the shared {@code ReactiveRedisConnectionFactory} auto-configured
     * by Spring Boot — no local factory bean.
     */
    @Bean("gofpReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, Object> gofpReactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, Object> serializationContext =
                RedisSerializationContext.<String, Object>newSerializationContext()
                        .key(stringSerializer)
                        .value(jsonSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(jsonSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
