package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.billing.BillingCompensationAdapter;
import com.yowyob.tiibntick.core.dispute.domain.enums.CompensationMethod;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration for
 * {@link DeliveryStatusAdapter} and {@link BillingCompensationAdapter} — both were found
 * unmigrated during post-merge verification (they live outside {@code DisputeKafkaEventPublisher}
 * as separate outbound-port adapters, so they were missed by the initial dispute-module pass).
 * Both now enqueue into the {@code yow-event-kernel} transactional outbox instead of sending to
 * Kafka directly, on the exact same topics/partition-keys/wire payload existing consumers expect.
 *
 * <p>Follows the same template as {@code DisputeKafkaEventPublisherOutboxIntegrationTest}.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = DeliveryStatusAndBillingCompensationOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        DeliveryStatusAndBillingCompensationOutboxIntegrationTest.PACKAGE_DISPUTED_TOPIC,
        DeliveryStatusAndBillingCompensationOutboxIntegrationTest.COMPENSATION_TOPIC})
@Tag("integration")
class DeliveryStatusAndBillingCompensationOutboxIntegrationTest {

    static final String PACKAGE_DISPUTED_TOPIC = "tnt.delivery.package.disputed";
    static final String COMPENSATION_TOPIC = "tnt.billing.compensation.initiated";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_dispute_delivery_billing_outbox_test")
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
    private DeliveryStatusAdapter deliveryStatusAdapter;

    @Autowired
    private BillingCompensationAdapter billingCompensationAdapter;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("markPackageAsDisputed() enqueues to the outbox; the poller delivers it to the "
            + "exact topic/key/payload format tnt-delivery-core already expects")
    void markPackageAsDisputedEnqueuesThenPollerDelivers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String packageId = "pkg-" + UUID.randomUUID();
        String disputeId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();

        deliveryStatusAdapter.markPackageAsDisputed(packageId, disputeId, tenantId)
                .block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(packageId, "Package", tenantId))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(PACKAGE_DISPUTED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "delivery-status-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, PACKAGE_DISPUTED_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, PACKAGE_DISPUTED_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(PACKAGE_DISPUTED_TOPIC);
            assertThat(received.key()).isEqualTo(packageId);

            JsonNode wirePayload = objectMapper.readTree(received.value());
            assertThat(wirePayload.get("packageId").asText()).isEqualTo(packageId);
            assertThat(wirePayload.get("disputeId").asText()).isEqualTo(disputeId);
        }

        StepVerifier.create(envelopeRepository.findByAggregateId(packageId, "Package", tenantId))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("initiateCompensationPayment() enqueues to the outbox and returns the payment "
            + "reference synchronously; the poller then delivers it to the compensation topic")
    void initiateCompensationPaymentEnqueuesThenPollerDelivers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String disputeId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        CompensationDetails details = CompensationDetails.approved(
                new BigDecimal("42.50"), "XAF", CompensationMethod.WALLET_CREDIT, "beneficiary-1");

        String paymentRef = billingCompensationAdapter
                .initiateCompensationPayment(disputeId, tenantId, details)
                .block(Duration.ofSeconds(10));

        assertThat(paymentRef).startsWith("COMP-");

        StepVerifier.create(envelopeRepository.findByAggregateId(disputeId, "Dispute", tenantId))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(COMPENSATION_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "billing-compensation-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, COMPENSATION_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, COMPENSATION_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(COMPENSATION_TOPIC);
            assertThat(received.key()).isEqualTo(disputeId);

            JsonNode wirePayload = objectMapper.readTree(received.value());
            assertThat(wirePayload.get("paymentRef").asText()).isEqualTo(paymentRef);
            assertThat(wirePayload.get("disputeId").asText()).isEqualTo(disputeId);
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

        @Bean(name = "disputeObjectMapper")
        ObjectMapper disputeObjectMapper() {
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
        DeliveryStatusAdapter deliveryStatusAdapter(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("disputeObjectMapper") ObjectMapper disputeObjectMapper) {
            return new DeliveryStatusAdapter(publishEventUseCase, disputeObjectMapper);
        }

        @Bean
        BillingCompensationAdapter billingCompensationAdapter(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("disputeObjectMapper") ObjectMapper disputeObjectMapper) {
            return new BillingCompensationAdapter(publishEventUseCase, disputeObjectMapper);
        }
    }
}
