package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * End-to-end integration test for {@link GofpEventPublisherPortAdapter}, recreating the
 * coverage lost when the module was resynced with its BFF origin on 2026-07-23 (see
 * {@code docs/knowledge/known-issues.md} #23) — proves {@code publish(topic, payload)}
 * really enqueues into the {@code yow-event-kernel} transactional outbox (not a
 * fire-and-forget send) and that {@code OutboxPollerService} relays it to the exact
 * requested Kafka topic with the raw JSON payload, matching what {@code MatchingUseCase}
 * (the adapter's only current caller) expects downstream consumers to receive.
 *
 * <p>Same Testcontainers-Postgres + embedded-Kafka pattern used across every other
 * outbox-migrated module (see {@code MarketKafkaEventPublisherOutboxIntegrationTest}).
 *
 * @author MANFOUO BRAUN
 */
@SpringBootTest(classes = GofpEventPublisherPortAdapterOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {GofpEventPublisherPortAdapterOutboxIntegrationTest.TEST_TOPIC})
@Tag("integration")
class GofpEventPublisherPortAdapterOutboxIntegrationTest {

    static final String TEST_TOPIC = "tnt.gofp.test.candidates-ranked";
    private static final String SOLUTION_CODE = "TNT";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_gofp_outbox_test")
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
    private GofpEventPublisherPortAdapter publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    /** Minimal stand-in payload — mirrors the {@code Map<String,Object>} shape MatchingUseCase publishes. */
    record TestRankedCandidatesPayload(
            @JsonProperty("announcementId") String announcementId,
            @JsonProperty("freelancerCount") int freelancerCount) {
    }

    @Test
    @DisplayName("publish(topic, payload) enqueues to the outbox (PENDING); the poller then "
            + "delivers the raw payload JSON to the exact requested Kafka topic")
    void publishEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String announcementId = UUID.randomUUID().toString();
        TestRankedCandidatesPayload payload = new TestRankedCandidatesPayload(announcementId, 3);
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        publisher.publish(TEST_TOPIC, payload).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByEventType(
                        "TestRankedCandidatesPayload", before, LocalDateTime.now().plusSeconds(5),
                        SOLUTION_CODE, 10))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(TEST_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "gofp-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, TEST_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, TEST_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(TEST_TOPIC);

            // Raw payload JSON — no outbox envelope wrapper.
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("announcementId").asText()).isEqualTo(announcementId);
            assertThat(wire.get("freelancerCount").asInt()).isEqualTo(3);
            assertThat(wire.has("eventType")).isFalse();
            assertThat(wire.has("payload")).isFalse();
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByEventType(
                        "TestRankedCandidatesPayload", before, LocalDateTime.now().plusSeconds(5),
                        SOLUTION_CODE, 10))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
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
            Map<String, Object> configs = new HashMap<>();
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configs);
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        GofpEventPublisherPortAdapter gofpEventPublisherPortAdapter(
                PublishEventUseCase publishEventUseCase) {
            return new GofpEventPublisherPortAdapter(
                    publishEventUseCase, new ObjectMapper().registerModule(new JavaTimeModule()));
        }
    }
}
