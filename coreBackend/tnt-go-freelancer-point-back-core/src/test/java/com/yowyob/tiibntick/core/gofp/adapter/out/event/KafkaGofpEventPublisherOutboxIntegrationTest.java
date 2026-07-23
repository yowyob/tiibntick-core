package com.yowyob.tiibntick.core.gofp.adapter.out.event;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link KafkaGofpEventPublisher} now enqueues GOFP events into the {@code yow-event-kernel}
 * transactional outbox instead of fire-and-forgetting {@code KafkaTemplate.send(...)}, and
 * {@code OutboxPollerService} relays them asynchronously — with the exact same flat JSON
 * payloads, message keys and {@code gofp.*} topics as before.
 *
 * <p>Same test pattern as {@code KafkaDeliveryEventPublisherOutboxIntegrationTest}
 * (the tnt-delivery-core pilot): real PostgreSQL Testcontainer for the event_bus schema,
 * embedded Kafka broker for the Kafka-side assertions.
 */
@SpringBootTest(classes = KafkaGofpEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaGofpEventPublisherOutboxIntegrationTest.ANNOUNCEMENT_TOPIC,
        KafkaGofpEventPublisherOutboxIntegrationTest.DELIVERY_TOPIC})
@Tag("integration")
class KafkaGofpEventPublisherOutboxIntegrationTest {

    static final String ANNOUNCEMENT_TOPIC = "gofp.announcement.published";
    static final String DELIVERY_TOPIC = "gofp.delivery.completed";

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
    private KafkaGofpEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publishAnnouncementPublished() enqueues to the outbox (PENDING, not yet on Kafka); "
            + "the poller then delivers it to the exact topic/payload format consumers expect")
    void publishAnnouncementEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID announcementId = UUID.randomUUID();
        UUID clientActorId = UUID.randomUUID();

        publisher.publishAnnouncementPublished(announcementId, clientActorId)
                .block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        announcementId.toString(), "Announcement",
                        KafkaGofpEventPublisher.GOFP_TENANT_MARKER))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(ANNOUNCEMENT_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(announcementId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "gofp-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, ANNOUNCEMENT_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, ANNOUNCEMENT_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(ANNOUNCEMENT_TOPIC);
            assertThat(received.key()).isEqualTo(announcementId.toString());

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("announcementId").asText()).isEqualTo(announcementId.toString());
            assertThat(wire.get("clientActorId").asText()).isEqualTo(clientActorId.toString());
            assertThat(wire.get("eventType").asText()).isEqualTo("ANNOUNCEMENT_PUBLISHED");
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        announcementId.toString(), "Announcement",
                        KafkaGofpEventPublisher.GOFP_TENANT_MARKER))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishDeliveryCompleted() tolerates the null clientActorId its caller "
            + "(DeliveryCoreService.completeDelivery) actually passes — the old Map.of payload NPE'd")
    void publishDeliveryCompletedWithNullClientActorId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID deliveryId = UUID.randomUUID();
        UUID freelancerActorId = UUID.randomUUID();

        publisher.publishDeliveryCompleted(deliveryId, freelancerActorId, null)
                .block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        deliveryId.toString(), "Delivery",
                        KafkaGofpEventPublisher.GOFP_TENANT_MARKER))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(DELIVERY_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "gofp-outbox-delivery-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, DELIVERY_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, DELIVERY_TOPIC, Duration.ofSeconds(15));
            assertThat(received.key()).isEqualTo(deliveryId.toString());

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("deliveryId").asText()).isEqualTo(deliveryId.toString());
            assertThat(wire.get("freelancerActorId").asText()).isEqualTo(freelancerActorId.toString());
            assertThat(wire.get("clientActorId").isNull()).isTrue();
            assertThat(wire.get("eventType").asText()).isEqualTo("DELIVERY_COMPLETED");
        }
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
        KafkaGofpEventPublisher kafkaGofpEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("tntObjectMapper") ObjectMapper tntObjectMapper) {
            return new KafkaGofpEventPublisher(publishEventUseCase, tntObjectMapper);
        }
    }
}
