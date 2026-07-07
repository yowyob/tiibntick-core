package com.yowyob.tiibntick.core.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for tnt-realtime-core.
 *
 * <p>Configures:</p>
 * <ul>
 *   <li>{@link ReactiveStringRedisTemplate} — for all key/value and pub-sub operations.</li>
 *   <li>{@link ReactiveRedisMessageListenerContainer} — for subscribing to Redis pub-sub
 *       channels (used by {@code RedisTopicMessageListener} and {@code FluxSseEmitter}).</li>
 * </ul>
 *
 * <p>The connection factory is provided by tnt-bootstrap via Spring Boot
 * autoconfiguration (spring.data.redis.* properties).</p>
 *
 * @author MANFOUO Braun
 */
@Configuration
public class RedisRealtimeConfig {

    /**
     * Reactive String Redis template configured with String serializers on both
     * key and value channels. All realtime-core Redis operations use JSON strings.
     *
     * @param connectionFactory the reactive Redis connection factory
     * @return configured ReactiveStringRedisTemplate
     */
    @Bean("realtimeRedisTemplate")
    public ReactiveStringRedisTemplate realtimeRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext(stringSerializer)
                        .key(stringSerializer)
                        .value(stringSerializer)
                        .hashKey(stringSerializer)
                        .hashValue(stringSerializer)
                        .build();

        return new ReactiveStringRedisTemplate(connectionFactory, serializationContext);
    }

    /**
     * Reactive Redis message listener container for pub-sub subscriptions.
     * Used by {@code RedisTopicMessageListener} (WebSocket broadcast) and
     * {@code FluxSseEmitter} (SSE streaming).
     *
     * @param connectionFactory the reactive Redis connection factory
     * @return configured message listener container
     */
    @Bean("realtimeRedisListenerContainer")
    public ReactiveRedisMessageListenerContainer realtimeRedisListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
}
