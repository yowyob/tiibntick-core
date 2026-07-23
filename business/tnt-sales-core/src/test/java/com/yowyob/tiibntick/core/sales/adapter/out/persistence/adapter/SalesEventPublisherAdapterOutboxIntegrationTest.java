package com.yowyob.tiibntick.core.sales.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.sales.domain.event.SalesOrderCancelledEvent;
import com.yowyob.tiibntick.core.sales.domain.event.SalesOrderConfirmedEvent;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link SalesEventPublisherAdapter} now enqueues events into the
 * {@code yow-event-kernel} transactional outbox instead of sending to Kafka
 * directly, and {@code OutboxPollerService} relays them asynchronously — on the
 * exact same topic and wire payload format (a plain JSON serialization of the
 * event record, no wrapper envelope) that existing consumers expect.
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via
 * yow-event-kernel's own Liquibase changelog) and an embedded Kafka broker (no
 * Testcontainers Kafka container — this repo standardises on spring-kafka-test's
 * embedded broker for Kafka-side assertions).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = SalesEventPublisherAdapterOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        SalesEventPublisherAdapterOutboxIntegrationTest.ORDER_CONFIRMED_TOPIC,
        SalesEventPublisherAdapterOutboxIntegrationTest.ORDER_CANCELLED_TOPIC})
@Tag("integration")
class SalesEventPublisherAdapterOutboxIntegrationTest {

    static final String ORDER_CONFIRMED_TOPIC = "tnt.sales.order.confirmed";
    static final String ORDER_CANCELLED_TOPIC = "tnt.sales.order.cancelled";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_sales_outbox_test")
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
    private SalesEventPublisherAdapter publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publishOrderConfirmed() enqueues to the outbox (PENDING, not yet on Kafka); "
            + "the poller then delivers it to the exact topic/payload format consumers expect")
    void publishOrderConfirmedEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SalesOrderConfirmedEvent event = new SalesOrderConfirmedEvent(
                orderId, tenantId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SO-2026-0001", new BigDecimal("2500.00"),
                "XAF", "NORMAL", Instant.now());

        publisher.publishOrderConfirmed(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        orderId.toString(), "SalesOrder", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(ORDER_CONFIRMED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "sales-outbox-confirmed-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, ORDER_CONFIRMED_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, ORDER_CONFIRMED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(ORDER_CONFIRMED_TOPIC);
            assertThat(received.key()).isEqualTo(orderId.toString());

            // Exact same wire format as the old adapter: a plain JSON serialization of the
            // event record, no wrapper envelope.
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("orderId").asText()).isEqualTo(orderId.toString());
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wire.get("orderNumber").asText()).isEqualTo("SO-2026-0001");
            assertThat(wire.get("totalAmount").asDouble()).isEqualTo(2500.00);
            assertThat(wire.get("currency").asText()).isEqualTo("XAF");
            assertThat(wire.get("priority").asText()).isEqualTo("NORMAL");
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        orderId.toString(), "SalesOrder", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishOrderCancelled() enqueues to the outbox (PENDING, not yet on Kafka); "
            + "the poller then delivers it to the exact topic/payload format consumers expect")
    void publishOrderCancelledEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SalesOrderCancelledEvent event = new SalesOrderCancelledEvent(
                orderId, tenantId, UUID.randomUUID(),
                "SO-2026-0002", "Client requested cancellation", Instant.now());

        publisher.publishOrderCancelled(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        orderId.toString(), "SalesOrder", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(ORDER_CANCELLED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "sales-outbox-cancelled-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, ORDER_CANCELLED_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, ORDER_CANCELLED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(ORDER_CANCELLED_TOPIC);
            assertThat(received.key()).isEqualTo(orderId.toString());

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("orderId").asText()).isEqualTo(orderId.toString());
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wire.get("orderNumber").asText()).isEqualTo("SO-2026-0002");
            assertThat(wire.get("cancelReason").asText()).isEqualTo("Client requested cancellation");
        }

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        orderId.toString(), "SalesOrder", tenantId.toString()))
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
        SalesEventPublisherAdapter salesEventPublisherAdapter(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("tntObjectMapper") ObjectMapper tntObjectMapper) {
            return new SalesEventPublisherAdapter(publishEventUseCase, tntObjectMapper);
        }
    }
}
