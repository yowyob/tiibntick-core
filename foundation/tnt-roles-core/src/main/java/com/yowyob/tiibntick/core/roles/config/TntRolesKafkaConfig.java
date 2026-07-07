package com.yowyob.tiibntick.core.roles.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated String/String Kafka consumer factory for tnt-roles-core, used by
 * {@link com.yowyob.tiibntick.core.roles.adapter.in.kafka.PermissionCacheInvalidationListener}.
 *
 * <p>Not reused from the bootstrap-shared {@code kafkaListenerContainerFactory} because that
 * one is bound to the global {@code spring.kafka.consumer.value-deserializer}
 * ({@code ByteArrayDeserializer} per {@code application.yml}) — this module's listener
 * expects JSON text payloads, same pattern as {@code NotifyCoreAutoConfiguration}.
 *
 * @author MANFOUO Braun
 */
@Configuration
public class TntRolesKafkaConfig {

    @Bean("rolesConsumerFactory")
    @ConditionalOnMissingBean(name = "rolesConsumerFactory")
    public ConsumerFactory<String, String> rolesConsumerFactory(Environment env) {
        String bootstrapServers = env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092");
        String groupId = env.getProperty("spring.kafka.consumer.group-id", "tnt-roles-core");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("rolesKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "rolesKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> rolesKafkaListenerContainerFactory(Environment env) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rolesConsumerFactory(env));
        factory.setConcurrency(1);
        return factory;
    }
}
