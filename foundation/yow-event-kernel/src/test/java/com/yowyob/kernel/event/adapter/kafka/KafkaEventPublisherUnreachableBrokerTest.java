package com.yowyob.kernel.event.adapter.kafka;

import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("KafkaEventPublisher with no broker: an error signal, never a thrown exception")
class KafkaEventPublisherUnreachableBrokerTest {

    @Test
    void unreachableBrokerIsASignalNotAnException() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        KafkaEventPublisher publisher = new KafkaEventPublisher(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg)));

        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString()).eventType("T").aggregateId("a").aggregateType("A")
                .tenantId("t").solutionCode("TNT").payload("{}").kafkaTopic("topic").build();

        // building the pipeline must not touch the network or throw
        Mono<Void>[] pipeline = new Mono[1];
        assertThatCode(() -> pipeline[0] = publisher.publish(envelope)).doesNotThrowAnyException();

        StepVerifier.create(pipeline[0]).expectError().verify(Duration.ofSeconds(10));
    }
}
