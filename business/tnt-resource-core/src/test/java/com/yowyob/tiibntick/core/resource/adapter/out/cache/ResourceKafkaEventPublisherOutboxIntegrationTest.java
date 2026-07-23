package com.yowyob.tiibntick.core.resource.adapter.out.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.resource.domain.event.FreelancerVehicleAssignedToMissionEvent;
import com.yowyob.tiibntick.core.resource.domain.event.VehicleRegisteredEvent;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
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
 * {@link ResourceKafkaEventPublisher} now enqueues events into the
 * {@code yow-event-kernel} transactional outbox instead of sending to Kafka
 * directly, and {@code OutboxPollerService} relays them asynchronously — on the
 * exact same topic, key, and wire payload format (a plain JSON serialization of the
 * event record, no wrapper envelope) that existing consumers expect.
 *
 * <p>Uses a real PostgreSQL Testcontainer (event_bus schema migrated via
 * yow-event-kernel's own Liquibase changelog) and an embedded Kafka broker (no
 * Testcontainers Kafka container — this repo standardises on spring-kafka-test's
 * embedded broker for Kafka-side assertions).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = ResourceKafkaEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        ResourceKafkaEventPublisherOutboxIntegrationTest.VEHICLE_REGISTERED_TOPIC,
        ResourceKafkaEventPublisherOutboxIntegrationTest.FL_VEHICLE_ASSIGNED_MISSION_TOPIC})
@Tag("integration")
class ResourceKafkaEventPublisherOutboxIntegrationTest {

    static final String VEHICLE_REGISTERED_TOPIC = "tnt.resource.vehicle.registered";
    static final String FL_VEHICLE_ASSIGNED_MISSION_TOPIC = "tnt.vehicle.assigned_to_mission";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_resource_outbox_test")
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
    private ResourceKafkaEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish(VehicleRegisteredEvent) enqueues to the outbox (PENDING, not yet on "
            + "Kafka); the poller then delivers it keyed by vehicleId with the exact payload format")
    void publishVehicleRegisteredEnqueuesToOutboxThenPollerDeliversToKafka() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID vehicleId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        VehicleRegisteredEvent event = VehicleRegisteredEvent.of(
                vehicleId, tenantId, agencyId, "CE-1234-AB", VehicleType.VAN, 1200.0, 8.5);

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. The envelope is durably persisted PENDING — nothing sent to Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        vehicleId.toString(), "Vehicle", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(VEHICLE_REGISTERED_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getTenantId()).isEqualTo(tenantId.toString());
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.eventId().toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "resource-outbox-vehicle-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, VEHICLE_REGISTERED_TOPIC);

            // 2. Trigger one poll cycle directly (what OutboxPollerService.scheduledPoll() does
            //    on a fixed delay in production) — avoids waiting on real time in the test.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, VEHICLE_REGISTERED_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(VEHICLE_REGISTERED_TOPIC);
            // Key preserved: the old adapter keyed vehicle events by vehicleId.
            assertThat(received.key()).isEqualTo(vehicleId.toString());

            // Exact same wire format as the old adapter: a plain JSON serialization of the
            // event record, no wrapper envelope.
            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("vehicleId").asText()).isEqualTo(vehicleId.toString());
            assertThat(wire.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wire.get("registrationNumber").asText()).isEqualTo("CE-1234-AB");
        }

        // 3. Once relayed, the envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        vehicleId.toString(), "Vehicle", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publish(FreelancerVehicleAssignedToMissionEvent) enqueues to the outbox using "
            + "ownerOrgId as the envelope tenant id (no tenantId in this domain event), keyed by "
            + "vehicleId as before")
    void publishFreelancerVehicleAssignedToMissionUsesOwnerOrgIdAsTenant() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID vehicleId = UUID.randomUUID();
        UUID ownerOrgId = UUID.randomUUID();
        String missionId = "MISSION-77";
        FreelancerVehicleAssignedToMissionEvent event = FreelancerVehicleAssignedToMissionEvent.of(
                vehicleId, ownerOrgId, VehicleType.MOTORCYCLE, missionId);

        publisher.publish(event).block(Duration.ofSeconds(10));

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        vehicleId.toString(), "FreelancerVehicle", ownerOrgId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(FL_VEHICLE_ASSIGNED_MISSION_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(vehicleId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "resource-outbox-flvehicle-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, FL_VEHICLE_ASSIGNED_MISSION_TOPIC);

            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, FL_VEHICLE_ASSIGNED_MISSION_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(FL_VEHICLE_ASSIGNED_MISSION_TOPIC);
            assertThat(received.key()).isEqualTo(vehicleId.toString());

            JsonNode wire = objectMapper.readTree(received.value());
            assertThat(wire.get("vehicleId").asText()).isEqualTo(vehicleId.toString());
            assertThat(wire.get("ownerOrgId").asText()).isEqualTo(ownerOrgId.toString());
            assertThat(wire.get("missionId").asText()).isEqualTo(missionId);
        }

        StepVerifier.create(envelopeRepository.findByAggregateId(
                        vehicleId.toString(), "FreelancerVehicle", ownerOrgId.toString()))
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
        ResourceKafkaEventPublisher resourceKafkaEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("tntObjectMapper") ObjectMapper tntObjectMapper) {
            return new ResourceKafkaEventPublisher(publishEventUseCase, tntObjectMapper);
        }
    }
}
