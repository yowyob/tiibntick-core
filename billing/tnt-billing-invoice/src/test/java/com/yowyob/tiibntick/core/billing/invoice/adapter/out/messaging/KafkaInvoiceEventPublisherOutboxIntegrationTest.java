package com.yowyob.tiibntick.core.billing.invoice.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceCancelled;
import com.yowyob.tiibntick.core.billing.invoice.domain.event.InvoiceGenerated;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.Money;
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
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link KafkaInvoiceEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly via {@code KafkaTemplate.send(...)},
 * and {@code OutboxPollerService} relays them asynchronously — on the exact same topic and
 * wire payload format (the raw serialised domain event, no wrapper) already consumed
 * downstream (e.g. tnt-accounting-core).
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via yow-event-kernel's
 * own Liquibase changelog) and an embedded Kafka broker, mirroring
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest} in tnt-delivery-core (the pilot
 * for this migration pattern).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaInvoiceEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {KafkaInvoiceEventPublisherOutboxIntegrationTest.INVOICE_EVENTS_TOPIC})
@Tag("integration")
class KafkaInvoiceEventPublisherOutboxIntegrationTest {

    static final String INVOICE_EVENTS_TOPIC = "tnt.billing.invoice.events";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_invoice_outbox_test")
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
    private KafkaInvoiceEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish() enqueues to the outbox (PENDING, not yet on Kafka); the poller then "
            + "delivers it to the exact topic/payload format consumers expect")
    void publishEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID invoiceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        InvoiceGenerated event = new InvoiceGenerated(
                invoiceId, "TNT-FACT-AGY001-2026-000001", "MISSION-123",
                Money.of(15_000, "XAF"), tenantId, Instant.now());

        publisher.publish(event, tenantId).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        invoiceId.toString(), "Invoice", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(INVOICE_EVENTS_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "invoice-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, INVOICE_EVENTS_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            // Both tests in this class share the single tnt.billing.invoice.events topic, so
            // fetch all records and select ours by key (unlike the delivery pilot, whose two
            // tests each had a dedicated topic and could use getSingleRecord).
            ConsumerRecord<String, String> received = findRecordByKey(
                    rawConsumer, INVOICE_EVENTS_TOPIC, invoiceId.toString());

            assertThat(received.topic()).isEqualTo(INVOICE_EVENTS_TOPIC);
            assertThat(received.key()).isEqualTo(invoiceId.toString());

            // The wire format is the raw serialised domain event — no outbox wrapper.
            JsonNode wireEvent = objectMapper.readTree(received.value());
            assertThat(wireEvent.get("invoiceId").asText()).isEqualTo(invoiceId.toString());
            assertThat(wireEvent.get("invoiceNumber").asText()).isEqualTo("TNT-FACT-AGY001-2026-000001");
            assertThat(wireEvent.get("missionId").asText()).isEqualTo("MISSION-123");
            assertThat(wireEvent.get("amount").get("currency").asText()).isEqualTo("XAF");
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        invoiceId.toString(), "Invoice", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishAll() batches an InvoiceCancelled event through the outbox to the same topic")
    void publishAllRoutesInvoiceCancelledToOutbox() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        InvoiceCancelled event = new InvoiceCancelled(invoiceId, "client requested", Instant.now());

        publisher.publishAll(List.of(event), tenantId).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        invoiceId.toString(), "Invoice", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(INVOICE_EVENTS_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "invoice-outbox-cancelled-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, INVOICE_EVENTS_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received = findRecordByKey(
                    rawConsumer, INVOICE_EVENTS_TOPIC, invoiceId.toString());
            assertThat(received.topic()).isEqualTo(INVOICE_EVENTS_TOPIC);
            assertThat(received.key()).isEqualTo(invoiceId.toString());
        }
    }

    /**
     * Polls until a record with the given key appears on the topic (up to 15s). Needed because
     * this class' two tests share one topic — a consumer reading from {@code earliest} may see
     * the other test's record as well, which breaks {@code getSingleRecord}'s single-record
     * assumption.
     */
    private static ConsumerRecord<String, String> findRecordByKey(
            Consumer<String, String> consumer, String topic, String key) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500)).records(topic)) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " received on " + topic + " within 15s");
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

        @Bean(name = "invoiceObjectMapper")
        ObjectMapper invoiceObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean(name = "tntKafkaTemplate")
        org.springframework.kafka.core.KafkaTemplate<String, String> tntKafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> configs = new HashMap<>();
            configs.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    broker.getBrokersAsString());
            configs.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            configs.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            return new org.springframework.kafka.core.KafkaTemplate<>(
                    new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(configs));
        }

        @Bean
        KafkaInvoiceEventPublisher kafkaInvoiceEventPublisher(
                PublishEventUseCase publishEventUseCase,
                PublishEventBatchUseCase publishEventBatchUseCase,
                @Qualifier("invoiceObjectMapper") ObjectMapper invoiceObjectMapper) {
            return new KafkaInvoiceEventPublisher(publishEventUseCase, publishEventBatchUseCase, invoiceObjectMapper);
        }
    }
}
