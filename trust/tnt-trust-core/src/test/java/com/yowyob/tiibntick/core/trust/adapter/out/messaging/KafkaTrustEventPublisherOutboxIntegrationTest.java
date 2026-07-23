package com.yowyob.tiibntick.core.trust.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration for
 * {@code tnt-trust-core}: {@link KafkaTrustEventPublisherAdapter} now enqueues trust
 * events into the {@code yow-event-kernel} transactional outbox instead of sending to
 * Kafka directly, and {@code OutboxPollerService} relays them asynchronously — on the
 * exact same {@code yow.trust.events} topic and {@code TrustEventKafkaMessage} wire
 * format that the {@code yow-trust-event} Kernel microservice already consumes.
 *
 * <p>Uses a real PostgreSQL Testcontainer (only the {@code event_bus} schema is
 * migrated, via {@code yow-event-kernel}'s own Liquibase changelog) and an embedded
 * Kafka broker — same pattern as
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest} in tnt-delivery-core
 * (the P5 pilot).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaTrustEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = KafkaTrustEventPublisherOutboxIntegrationTest.TRUST_TOPIC)
@Tag("integration")
class KafkaTrustEventPublisherOutboxIntegrationTest {

    static final String TRUST_TOPIC = "yow.trust.events";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_trust_outbox_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/yow-event-kernel-master.yaml",
                    new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    @DynamicPropertySource
    static void dynamicProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired
    private KafkaTrustEventPublisherAdapter publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish() enqueues to the outbox (PENDING, not yet on Kafka); the poller then "
            + "delivers the exact TrustEventKafkaMessage wire format yow-trust-event expects")
    void publishEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        final String tenantId = UUID.randomUUID().toString();
        final String proofId = "proof-" + UUID.randomUUID();
        final DeliveryProofRecord proof = new DeliveryProofRecord(
                proofId, "mission-001", "package-001", "actor-001", tenantId,
                "a".repeat(64), null, 3.848, 11.502, LocalDateTime.now());
        final LogisticTrustEvent event =
                LogisticTrustEvent.forDeliveryProof(proof, "mission-001", "actor-001");

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        proofId, "DELIVERY_PROOF", tenantId))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(TRUST_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId);
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.getCorrelationId());
                })
                .verifyComplete();

        final Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "trust-outbox-it", true));
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer =
                     new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, TRUST_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll()
            //    does on a fixed delay in production) — avoids waiting on real time.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            final ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, TRUST_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(TRUST_TOPIC);
            // Kafka message key is still the entityId — per-entity partition ordering.
            assertThat(received.key()).isEqualTo(proofId);

            // Wire format is byte-for-byte the pre-migration TrustEventKafkaMessage contract.
            final JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("correlationId").asText()).isEqualTo(event.getCorrelationId());
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId);
            assertThat(wire.get("solutionCode").asText()).isEqualTo("TNT");
            assertThat(wire.get("eventType").asText()).isEqualTo("DELIVERY_PROOF_RECORDED");
            assertThat(wire.get("entityType").asText()).isEqualTo("DELIVERY_PROOF");
            assertThat(wire.get("entityId").asText()).isEqualTo(proofId);
            assertThat(wire.get("sourceService").asText()).isEqualTo("tnt-trust");
            assertThat(wire.get("payload").asText()).contains(proofId);
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        proofId, "DELIVERY_PROOF", tenantId))
                .assertNext(envelope ->
                        assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Configuration
    @Import({
            R2dbcAutoConfiguration.class,
            R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            YowEventKernelAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean(name = "tntObjectMapper")
        ObjectMapper tntObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean(name = "tntKafkaTemplate")
        KafkaTemplate<String, String> tntKafkaTemplate(EmbeddedKafkaBroker broker) {
            final Map<String, Object> configs = new HashMap<>();
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            final ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configs);
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        KafkaTrustEventPublisherAdapter kafkaTrustEventPublisherAdapter(
                PublishEventUseCase publishEventUseCase,
                PublishEventBatchUseCase publishEventBatchUseCase,
                @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
            return new KafkaTrustEventPublisherAdapter(
                    publishEventUseCase, publishEventBatchUseCase, objectMapper);
        }
    }
}
