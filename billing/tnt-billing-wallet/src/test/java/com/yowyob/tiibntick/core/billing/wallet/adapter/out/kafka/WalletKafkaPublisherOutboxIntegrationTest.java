package com.yowyob.tiibntick.core.billing.wallet.adapter.out.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.PaymentInitiated;
import com.yowyob.tiibntick.core.billing.wallet.domain.event.WalletCredited;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
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
 * {@link WalletKafkaPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly, and {@code OutboxPollerService}
 * relays them asynchronously — on the same {@code tnt.billing.wallet.*} topics and wire
 * payload format (the raw serialised domain event) already consumed downstream.
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via yow-event-kernel's
 * own Liquibase changelog) and an embedded Kafka broker, mirroring
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest} in tnt-delivery-core (the pilot
 * for this migration pattern).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = WalletKafkaPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        WalletKafkaPublisherOutboxIntegrationTest.WALLET_CREDITED_TOPIC,
        WalletKafkaPublisherOutboxIntegrationTest.PAYMENT_INITIATED_TOPIC})
@Tag("integration")
class WalletKafkaPublisherOutboxIntegrationTest {

    static final String WALLET_CREDITED_TOPIC = "tnt.billing.wallet.wallet-credited";
    static final String PAYMENT_INITIATED_TOPIC = "tnt.billing.wallet.payment-initiated";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_wallet_outbox_test")
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
    private WalletKafkaPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish(WalletCredited) enqueues to the outbox (PENDING); the poller then "
            + "delivers it keyed by walletId with the raw-event wire format consumers expect")
    void walletCreditedGoesThroughOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        WalletCredited event = new WalletCredited(
                walletId, userId, tenantId,
                Money.ofXAF(5_000), Money.ofXAF(15_000), "REF-001");

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        walletId.toString(), "Wallet", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(WALLET_CREDITED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(walletId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "wallet-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, WALLET_CREDITED_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production).
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, WALLET_CREDITED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(WALLET_CREDITED_TOPIC);
            assertThat(received.key()).isEqualTo(walletId.toString());

            // The wire format is the raw serialised domain event — no outbox wrapper.
            JsonNode wireEvent = objectMapper.readTree(received.value());
            assertThat(wireEvent.get("walletId").asText()).isEqualTo(walletId.toString());
            assertThat(wireEvent.get("userId").asText()).isEqualTo(userId.toString());
            assertThat(wireEvent.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wireEvent.get("referenceId").asText()).isEqualTo("REF-001");
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        walletId.toString(), "Wallet", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publish(PaymentInitiated) routes to its own topic through the outbox, keyed by paymentIntentId")
    void paymentInitiatedGoesThroughOutbox() throws Exception {
        UUID paymentIntentId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PaymentInitiated event = new PaymentInitiated(
                paymentIntentId, walletId, userId, tenantId,
                "INV-42", Money.ofXAF(10_000), PaymentChannel.MTN_MOMO, "+237600000000");

        publisher.publish(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        paymentIntentId.toString(), "Wallet", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(PAYMENT_INITIATED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(paymentIntentId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "wallet-outbox-payment-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, PAYMENT_INITIATED_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, PAYMENT_INITIATED_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(PAYMENT_INITIATED_TOPIC);
            assertThat(received.key()).isEqualTo(paymentIntentId.toString());
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

        @Bean(name = "walletObjectMapper")
        ObjectMapper walletObjectMapper() {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
        WalletKafkaPublisher walletKafkaPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("walletObjectMapper") ObjectMapper walletObjectMapper) {
            return new WalletKafkaPublisher(publishEventUseCase, walletObjectMapper);
        }
    }
}
