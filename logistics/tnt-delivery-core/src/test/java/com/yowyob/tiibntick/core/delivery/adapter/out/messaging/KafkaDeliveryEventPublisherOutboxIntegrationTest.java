package com.yowyob.tiibntick.core.delivery.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.delivery.domain.event.FreelancerOrgAssignedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 pilot migration:
 * {@link KafkaDeliveryEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly, and {@code OutboxPollerService}
 * relays them asynchronously — on the exact same topic and wire payload format that
 * {@code tnt-incident-core}/{@code tnt-sync-core}/{@code tnt-realtime-core}/
 * {@code tnt-market-back-core} already consume (see the topic-alignment fix this test
 * supersedes: {@code MissionStatusChangedTopicIntegrationTest}, Audit n°3 P6 / Audit n°5 P-01).
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via
 * {@code yow-event-kernel}'s own Liquibase changelog — same pattern as
 * {@code YowEventKernelIntegrationTest}) and an embedded Kafka broker (no Testcontainers
 * Kafka container — see that class' Javadoc for why this repo standardises on
 * {@code spring-kafka-test} for Kafka-side assertions).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaDeliveryEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaDeliveryEventPublisherOutboxIntegrationTest.MISSION_STATUS_TOPIC,
        KafkaDeliveryEventPublisherOutboxIntegrationTest.FREELANCER_ORG_TOPIC,
        KafkaDeliveryEventPublisherOutboxIntegrationTest.PACKAGE_UPDATED_TOPIC})
@Tag("integration")
class KafkaDeliveryEventPublisherOutboxIntegrationTest {

    static final String MISSION_STATUS_TOPIC = "tnt.delivery.mission.status.changed";
    static final String FREELANCER_ORG_TOPIC = "tnt.delivery.freelancer_org.assigned";
    static final String PACKAGE_UPDATED_TOPIC = "tnt.delivery.package.updated";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_delivery_outbox_test")
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
    private KafkaDeliveryEventPublisher publisher;

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

        UUID deliveryId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        MissionStatusChangedEvent event = new MissionStatusChangedEvent(
                deliveryId, tenantId,
                "IN_TRANSIT", "ASSIGNED",
                UUID.randomUUID(), null,
                "GO", parcelId,
                Instant.now());

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        // MissionStatusChangedEvent fans out to two topics: the dedicated mission-status
        // topic (tnt-incident-core, tnt-realtime-core, tnt-market-back-core) and the generic
        // package-updated topic (tnt-sync-core) — Audit n°5 P-01, tnt.delivery.package.updated
        // used to have no producer at all.
        java.util.List<com.yowyob.kernel.event.domain.model.DomainEventEnvelope> envelopes =
                envelopeRepository.findByAggregateId(deliveryId.toString(), "Delivery", tenantId.toString())
                        .collectList().block(Duration.ofSeconds(10));
        assertThat(envelopes).hasSize(2);
        assertThat(envelopes).extracting(
                com.yowyob.kernel.event.domain.model.DomainEventEnvelope::getKafkaTopic)
                .containsExactlyInAnyOrder(MISSION_STATUS_TOPIC, PACKAGE_UPDATED_TOPIC);
        assertThat(envelopes).allSatisfy(envelope -> {
            assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
            assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
        });

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "delivery-outbox-it", true));
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer =
                     new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, MISSION_STATUS_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, MISSION_STATUS_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(MISSION_STATUS_TOPIC);
            assertThat(received.key()).isEqualTo(deliveryId.toString());

            JsonNode wireEnvelope = objectMapper.readTree(received.value());
            assertThat(wireEnvelope.get("eventType").asText()).isEqualTo("MissionStatusChangedEvent");
            assertThat(wireEnvelope.get("aggregateId").asText()).isEqualTo(deliveryId.toString());
            assertThat(wireEnvelope.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wireEnvelope.get("payload").get("newStatus").asText()).isEqualTo("IN_TRANSIT");
        }

        // 3. Once relayed, both fanned-out envelopes transition to PUBLISHED.
        java.util.List<com.yowyob.kernel.event.domain.model.DomainEventEnvelope> publishedEnvelopes =
                envelopeRepository.findByAggregateId(deliveryId.toString(), "Delivery", tenantId.toString())
                        .collectList().block(Duration.ofSeconds(10));
        assertThat(publishedEnvelopes).hasSize(2)
                .allSatisfy(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED));
    }

    @Test
    @DisplayName("publishAll() batches a FreelancerOrgAssignedEvent to its own topic through the outbox")
    void publishAllRoutesFreelancerOrgEventToItsTopic() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        FreelancerOrgAssignedEvent event = new FreelancerOrgAssignedEvent(
                deliveryId, tenantId, "org-42", "OWNER", Instant.now());

        publisher.publishAll(java.util.List.of(event)).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        deliveryId.toString(), "Delivery", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(FREELANCER_ORG_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "delivery-outbox-freelancer-it", true));
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer =
                     new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, FREELANCER_ORG_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, FREELANCER_ORG_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(FREELANCER_ORG_TOPIC);
            assertThat(received.key()).isEqualTo(deliveryId.toString());
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

        @Bean(name = "deliveryObjectMapper")
        ObjectMapper deliveryObjectMapper() {
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
        KafkaDeliveryEventPublisher kafkaDeliveryEventPublisher(
                PublishEventUseCase publishEventUseCase,
                PublishEventBatchUseCase publishEventBatchUseCase,
                @Qualifier("deliveryObjectMapper") ObjectMapper deliveryObjectMapper) {
            return new KafkaDeliveryEventPublisher(publishEventUseCase, publishEventBatchUseCase, deliveryObjectMapper);
        }
    }
}
