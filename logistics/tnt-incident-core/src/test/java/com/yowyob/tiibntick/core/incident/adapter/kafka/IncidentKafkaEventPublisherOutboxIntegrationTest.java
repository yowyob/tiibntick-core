package com.yowyob.tiibntick.core.incident.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentCategory;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;
import com.yowyob.tiibntick.core.incident.domain.enums.PlatformType;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
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

/**
 * Lot L8: the incident events reach Kafka only on {@code tnt.incident.events} ({@link IncidentAggregateEventOutboxPublisher}
 * through the yow-event-kernel envelope store, envelope {@code PUBLISHED}); the twelve per-event topics
 * ({@code tnt.incident.created}, {@code tnt.incident.status.changed}…) and their publisher are gone. The record is
 * the §3.3 envelope, masked (no description), keyed by the incident id.
 *
 * <p>Follows the same template as {@code tnt-delivery-core}'s
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest}.
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = IncidentKafkaEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = TntTopics.INCIDENT_EVENTS)
@Tag("integration")
class IncidentKafkaEventPublisherOutboxIntegrationTest {

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
    private IncidentAggregateEventOutboxPublisher publisher;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("lot L8: a reported incident goes out once, on tnt.incident.events (no legacy tnt.incident.created), "
            + "masked, keyed by the incident id")
    void reportedIncidentOnTheAggregateTopic() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        UUID tenantId = UUID.randomUUID();
        Incident reported = Incident.create(tenantId, UUID.randomUUID(), PlatformType.AGENCY, UUID.randomUUID(),
                IncidentCategory.VEHICLE, IncidentType.VEHICLE_ENGINE_FAILURE, "Engine failure on N3",
                UUID.randomUUID(), ActorRole.AGENCY_DRIVER, List.of(UUID.randomUUID()));

        publisher.publish(reported, reported).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        reported.getId().toString(), "INCIDENT", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(TntTopics.INCIDENT_EVENTS);
                    assertThat(envelope.getEventType()).isEqualTo("IncidentReported");
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "incident-outbox-created-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, TntTopics.INCIDENT_EVENTS);
            // the other test of the class publishes on the same embedded topic: keep only this incident's records
            List<ConsumerRecord<String, String>> mine = new java.util.ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (mine.isEmpty() && System.currentTimeMillis() < deadline) {
                rawConsumer.poll(Duration.ofMillis(500)).records(TntTopics.INCIDENT_EVENTS).forEach(r -> {
                    if (reported.getId().toString().equals(r.key())) {
                        mine.add(r);
                    }
                });
            }
            assertThat(mine).hasSize(1);
            ConsumerRecord<String, String> received = mine.get(0);
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.path("eventType").asText()).isEqualTo("IncidentReported");
            assertThat(wire.path("schemaVersion").asInt()).isPositive();
            assertThat(wire.path("payload").path("type").asText()).isEqualTo("VEHICLE_ENGINE_FAILURE");
            assertThat(received.value()).doesNotContain("Engine failure on N3");
        }
    }

    @Test
    @DisplayName("lot L8: report then acknowledgement go out as IncidentReported, IncidentAcknowledged on "
            + "tnt.incident.events only (no legacy status topic)")
    void transitionsOnTheAggregateTopic() {
        UUID tenantId = UUID.randomUUID();
        Incident reported = Incident.create(tenantId, UUID.randomUUID(), PlatformType.AGENCY, UUID.randomUUID(),
                IncidentCategory.VEHICLE, IncidentType.VEHICLE_ENGINE_FAILURE, "x",
                UUID.randomUUID(), ActorRole.AGENCY_DRIVER, List.of());
        Incident acknowledged = reported.acknowledge();

        publisher.publish(acknowledged, acknowledged).block(Duration.ofSeconds(10));

        List<com.yowyob.kernel.event.domain.model.DomainEventEnvelope> envelopes = envelopeRepository
                .findByAggregateId(reported.getId().toString(), "INCIDENT", tenantId.toString())
                .collectList().block(Duration.ofSeconds(10));
        assertThat(envelopes).extracting(e -> e.getEventType())
                .containsExactlyInAnyOrder("IncidentReported", "IncidentAcknowledged");
        assertThat(envelopes).allSatisfy(e -> assertThat(e.getKafkaTopic()).isEqualTo(TntTopics.INCIDENT_EVENTS));
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
        IncidentAggregateEventOutboxPublisher incidentAggregateEventOutboxPublisher(
                PublishEventBatchUseCase publishEventBatchUseCase) {
            return new IncidentAggregateEventOutboxPublisher(publishEventBatchUseCase,
                    new org.springframework.beans.factory.support.StaticListableBeanFactory()
                            .getBeanProvider(TntEventMasking.class));
        }
    }
}
