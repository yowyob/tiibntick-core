package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.inventory.domain.event.PackageDepositedEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link InventoryKafkaEventPublisher} now enqueues events into the
 * {@code yow-event-kernel} transactional outbox instead of sending through
 * reactor-kafka's {@code KafkaSender} directly, and {@code OutboxPollerService}
 * relays them asynchronously — on the exact same topic, key, and wire payload
 * format (a plain JSON serialization of the event record, no wrapper envelope)
 * that existing consumers expect.
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via
 * yow-event-kernel's own Liquibase changelog) and an embedded Kafka broker (no
 * Testcontainers Kafka container — this repo standardises on spring-kafka-test's
 * embedded broker for Kafka-side assertions).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = InventoryKafkaEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        InventoryKafkaEventPublisherOutboxIntegrationTest.STOCK_LOW_TOPIC,
        InventoryKafkaEventPublisherOutboxIntegrationTest.PKG_DEPOSITED_TOPIC})
@Tag("integration")
class InventoryKafkaEventPublisherOutboxIntegrationTest {

    static final String STOCK_LOW_TOPIC = "tnt.inventory.stock.low";
    static final String PKG_DEPOSITED_TOPIC = "tnt.inventory.hub.package.deposited";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_inventory_outbox_test")
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
    private InventoryKafkaEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publishStockLow() enqueues to the outbox (PENDING, not yet on Kafka); the poller "
            + "then delivers it keyed by productId with the exact payload format consumers expect")
    void publishStockLowEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StockLowEvent event = new StockLowEvent(productId, warehouseId, tenantId, 3.0, 10.0, Instant.now());

        publisher.publishStockLow(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        productId.toString(), "StockEntry", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(STOCK_LOW_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "inventory-outbox-stocklow-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, STOCK_LOW_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, STOCK_LOW_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(STOCK_LOW_TOPIC);
            // Key preserved: the old adapter keyed stock-low events by productId.
            assertThat(received.key()).isEqualTo(productId.toString());

            // Exact same wire format as the old adapter: a plain JSON serialization of the
            // event record, no wrapper envelope.
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("productId").asText()).isEqualTo(productId.toString());
            assertThat(wire.get("warehouseId").asText()).isEqualTo(warehouseId.toString());
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wire.get("currentQuantity").asDouble()).isEqualTo(3.0);
            assertThat(wire.get("threshold").asDouble()).isEqualTo(10.0);
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        productId.toString(), "StockEntry", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishPackageDeposited() enqueues to the outbox; the poller then delivers it "
            + "keyed by trackingCode (preserved from the old adapter) with the same payload")
    void publishPackageDepositedPreservesTrackingCodeKey() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID entryId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String trackingCode = "TNT-TRK-0042";
        PackageDepositedEvent event = new PackageDepositedEvent(
                entryId, hubId, packageId, trackingCode, tenantId, Instant.now());

        publisher.publishPackageDeposited(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        entryId.toString(), "HubPackageEntry", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(PKG_DEPOSITED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(trackingCode);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "inventory-outbox-deposited-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, PKG_DEPOSITED_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, PKG_DEPOSITED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(PKG_DEPOSITED_TOPIC);
            // Key preserved: the old adapter keyed hub package events by trackingCode.
            assertThat(received.key()).isEqualTo(trackingCode);

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("hubPackageEntryId").asText()).isEqualTo(entryId.toString());
            assertThat(wire.get("hubId").asText()).isEqualTo(hubId.toString());
            assertThat(wire.get("packageId").asText()).isEqualTo(packageId.toString());
            assertThat(wire.get("trackingCode").asText()).isEqualTo(trackingCode);
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
        }

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        entryId.toString(), "HubPackageEntry", tenantId.toString()))
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
        InventoryKafkaEventPublisher inventoryKafkaEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("tntObjectMapper") ObjectMapper tntObjectMapper) {
            return new InventoryKafkaEventPublisher(publishEventUseCase, tntObjectMapper);
        }
    }
}
