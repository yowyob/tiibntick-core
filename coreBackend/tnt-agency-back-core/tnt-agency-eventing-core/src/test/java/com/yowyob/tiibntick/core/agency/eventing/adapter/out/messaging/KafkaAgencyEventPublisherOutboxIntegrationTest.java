package com.yowyob.tiibntick.core.agency.eventing.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.ContractSigned;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.MissionCreated;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link KafkaAgencyEventPublisher} — the publisher the audit cited by name for
 * "failures are swallowed" — now enqueues agency integration events into the
 * {@code yow-event-kernel} transactional outbox instead of sending to Kafka directly,
 * and {@code OutboxPollerService} relays them asynchronously — with the exact same
 * envelope wire format ({@code eventId, eventType, aggregateId, aggregateType, tenantId,
 * occurredAt, correlationId, sequence, payload}), message key ({@code tenantId}) and
 * event-type topic routing existing consumers already rely on.
 *
 * <p>Same test pattern as {@code KafkaDeliveryEventPublisherOutboxIntegrationTest}
 * (the tnt-delivery-core pilot): real PostgreSQL Testcontainer for the event_bus schema,
 * embedded Kafka broker for the Kafka-side assertions.
 */
@SpringBootTest(classes = KafkaAgencyEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaAgencyEventPublisherOutboxIntegrationTest.MISSION_TOPIC,
        KafkaAgencyEventPublisherOutboxIntegrationTest.CONTRACT_TOPIC})
@Tag("integration")
class KafkaAgencyEventPublisherOutboxIntegrationTest {

    static final String MISSION_TOPIC = "tnt.agency.mission.request";
    static final String CONTRACT_TOPIC = "tnt.agency.contract.events";
    static final String STAFF_TOPIC = "tnt.agency.staff.events";
    static final String DEFAULT_TOPIC = "tnt.agency.events";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_agency_eventing_outbox_test")
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
    private KafkaAgencyEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish() enqueues to the outbox (PENDING, not yet on Kafka); the poller then "
            + "delivers it with the same envelope wire format, key and topic routing as before")
    void publishEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID eventId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        MissionCreated event = new MissionCreated(
                eventId, missionId, tenantId, agencyId,
                UUID.randomUUID(), Instant.now(), Instant.now());

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        missionId.toString(), "AgencyMission", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(MISSION_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getCorrelationId()).isEqualTo(eventId.toString());
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "agency-eventing-outbox-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, MISSION_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, MISSION_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(MISSION_TOPIC);
            assertThat(received.key()).isEqualTo(tenantId.toString());

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("eventId").asText()).isEqualTo(eventId.toString());
            assertThat(wire.get("eventType").asText()).isEqualTo("MissionCreated");
            assertThat(wire.get("aggregateId").asText()).isEqualTo(missionId.toString());
            assertThat(wire.get("aggregateType").asText()).isEqualTo("AgencyMission");
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wire.get("correlationId").asText()).isEqualTo(eventId.toString());
            assertThat(wire.get("sequence").asLong()).isEqualTo(1L);
            assertThat(wire.get("payload").get("agencyId").asText()).isEqualTo(agencyId.toString());
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        missionId.toString(), "AgencyMission", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishAll() batches a ContractSigned event to the contract topic through the outbox")
    void publishAllRoutesContractSignedToContractTopic() {
        UUID eventId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ContractSigned event = new ContractSigned(
                eventId, contractId, tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "FREELANCER_AGREEMENT", Instant.now());

        publisher.publishAll(List.of(event)).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        contractId.toString(), event.getAggregateType(), tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(CONTRACT_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "agency-eventing-outbox-contract-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, CONTRACT_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, CONTRACT_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(CONTRACT_TOPIC);
            assertThat(received.key()).isEqualTo(tenantId.toString());
        }
    }

    @Test
    @DisplayName("publishAll() on an empty list is a no-op (does not hit the batch use case's "
            + "non-empty precondition)")
    void publishAllEmptyListIsNoOp() {
        StepVerifier.create(publisher.publishAll(List.of())).verifyComplete();
        StepVerifier.create(publisher.publishAll(null)).verifyComplete();
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
        KafkaAgencyEventPublisher kafkaAgencyEventPublisher(
                PublishEventUseCase publishEventUseCase,
                PublishEventBatchUseCase publishEventBatchUseCase) {
            return new KafkaAgencyEventPublisher(
                    publishEventUseCase, publishEventBatchUseCase,
                    new ObjectMapper().registerModule(new JavaTimeModule()),
                    STAFF_TOPIC, CONTRACT_TOPIC, MISSION_TOPIC, DEFAULT_TOPIC);
        }
    }
}
