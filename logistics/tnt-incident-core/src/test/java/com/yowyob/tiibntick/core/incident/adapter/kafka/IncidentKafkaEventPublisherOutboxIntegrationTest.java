package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentCategory;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;
import com.yowyob.tiibntick.core.incident.domain.enums.PlatformType;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.IncidentCreatedEvent;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.IncidentStatusChangedEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link IncidentKafkaEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of fire-and-forget {@code KafkaTemplate.send} calls, and
 * {@code OutboxPollerService} relays them asynchronously — same topics, same
 * raw-domain-event-JSON wire format and incident-id record key existing consumers
 * (tnt-delivery-core, tnt-dispute-core, tnt-billing-wallet, tnt-notify-core, tnt-trust-core)
 * already expect.
 *
 * <p>Follows the same template as {@code tnt-delivery-core}'s
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest}.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = IncidentKafkaEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        IncidentKafkaEventPublisherOutboxIntegrationTest.CREATED_TOPIC,
        IncidentKafkaEventPublisherOutboxIntegrationTest.STATUS_CHANGED_TOPIC})
@Tag("integration")
class IncidentKafkaEventPublisherOutboxIntegrationTest {

    static final String CREATED_TOPIC = "tnt.incident.created";
    static final String STATUS_CHANGED_TOPIC = "tnt.incident.status.changed";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_incident_outbox_test")
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
    private IncidentKafkaEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish(IncidentCreatedEvent) enqueues to the outbox (PENDING); the poller then "
            + "delivers it to tnt.incident.created with the raw event JSON and incident-id key")
    void publishCreatedGoesThroughOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID incidentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        IncidentCreatedEvent event = IncidentCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incidentId)
                .referenceCode("INC-2026-0001")
                .tenantId(tenantId)
                .agencyId(UUID.randomUUID())
                .missionId(UUID.randomUUID())
                .platform(PlatformType.AGENCY)
                .category(IncidentCategory.VEHICLE)
                .type(IncidentType.VEHICLE_ENGINE_FAILURE)
                .description("Engine failure on N3")
                .reportedByActorId(UUID.randomUUID())
                .reportedByRole(ActorRole.AGENCY_DRIVER)
                .affectedParcelIds(List.of(UUID.randomUUID()))
                .occurredAt(Instant.now())
                .build();

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. Durably persisted PENDING — nothing on Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        incidentId.toString(), "Incident", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(CREATED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.getEventId().toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "incident-outbox-created-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, CREATED_TOPIC);

            // 2. One poll cycle relays it.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, CREATED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(CREATED_TOPIC);
            assertThat(received.key()).isEqualTo(incidentId.toString());

            // Wire format: the raw domain event JSON, unchanged from the pre-migration
            // kafkaTemplate.send(topic, incidentId, objectMapper.writeValueAsString(event)).
            JsonNode wirePayload = objectMapper.readTree(received.value());
            assertThat(wirePayload.get("incidentId").asText()).isEqualTo(incidentId.toString());
            assertThat(wirePayload.get("referenceCode").asText()).isEqualTo("INC-2026-0001");
            assertThat(wirePayload.get("type").asText()).isEqualTo("VEHICLE_ENGINE_FAILURE");
        }

        // 3. Envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        incidentId.toString(), "Incident", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publish(IncidentStatusChangedEvent) routes to the status-changed topic through the outbox")
    void publishStatusChangedRoutesToItsTopic() {
        UUID incidentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        IncidentStatusChangedEvent event = IncidentStatusChangedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incidentId)
                .tenantId(tenantId)
                .missionId(UUID.randomUUID())
                .agencyId(UUID.randomUUID())
                .platform(PlatformType.AGENCY)
                .previousStatus(IncidentStatus.DETECTED)
                .newStatus(IncidentStatus.TRIAGED)
                .occurredAt(Instant.now())
                .build();

        publisher.publish(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        incidentId.toString(), "Incident", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(STATUS_CHANGED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                })
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

        @Bean(name = "incidentObjectMapper")
        ObjectMapper incidentObjectMapper() {
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
        IncidentKafkaEventPublisher incidentKafkaEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("incidentObjectMapper") ObjectMapper incidentObjectMapper) {
            return new IncidentKafkaEventPublisher(publishEventUseCase, incidentObjectMapper);
        }
    }
}
