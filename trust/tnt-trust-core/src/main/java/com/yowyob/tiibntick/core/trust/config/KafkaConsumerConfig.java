package com.yowyob.tiibntick.core.trust.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration — {@code KafkaConsumerConfig}.
 *
 * <p>Configures the Kafka consumer beans for {@code tnt-trust}: the consumer
 * factory backing {@code TrustCommittedEventConsumer}'s listener on
 * {@code yow.trust.events.committed}.
 *
 * <p><strong>No producer beans anymore</strong> (Chantier C · Audit n°3 · P5):
 * outbound trust events go through {@code yow-event-kernel}'s transactional
 * outbox (see {@code KafkaTrustEventPublisherAdapter}); the outbox poller owns
 * the actual Kafka producer. The previous {@code tntTrustProducerFactory}/
 * {@code tntTrustKafkaTemplate} beans were removed along with the
 * {@code trust_retry_queue} republish path that was their last user.
 *
 * @author MANFOUO Braun
 * @version 2.0
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Kafka consumer factory for {@code yow.trust.events.committed}, consumed by
     * {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     * Named (not the ambiguous {@code ConsumerFactory<String,String>} type) so
     * {@link TntTrustAutoConfiguration#tntTrustKafkaListenerContainerFactory} can
     * reference it directly instead of relying on Spring to disambiguate among
     * the dozen other modules' own consumer factories on the classpath.
     */
    @Bean("tntTrustConsumerFactory")
    @ConditionalOnMissingBean(name = "tntTrustConsumerFactory")
    public ConsumerFactory<String, String> tntTrustConsumerFactory() {
        final Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-trust-core");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
