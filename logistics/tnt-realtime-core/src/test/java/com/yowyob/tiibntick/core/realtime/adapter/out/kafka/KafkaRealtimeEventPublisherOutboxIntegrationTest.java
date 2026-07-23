package com.yowyob.tiibntick.core.realtime.adapter.out.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.realtime.domain.event.ActorConnectedEvent;
import com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceTrigger;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceDirection;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType;
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
 * {@link KafkaRealtimeEventPublisher} routes {@code GeofenceTriggerEvent} — the one business-
 * consequential event this adapter publishes — through the {@code yow-event-kernel}
 * transactional outbox, while every other event type it handles ({@code ActorConnectedEvent}
 * used here as a representative example) keeps going straight to Kafka via
 * {@code KafkaTemplate}, unchanged from before this migration.
 *
 * <p>See {@link KafkaRealtimeEventPublisher}'s class Javadoc for the full reasoning behind this
 * split (high-frequency ephemeral telemetry vs. one-shot business events).
 *
 * @author MANFOUO Braun
 */
@SpringBootTest(classes = KafkaRealtimeEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaRealtimeEventPublisherOutboxIntegrationTest.GEOFENCE_TOPIC,
        KafkaRealtimeEventPublisherOutboxIntegrationTest.ACTOR_CONNECTED_TOPIC})
@Tag("integration")
class KafkaRealtimeEventPublisherOutboxIntegrationTest {

    static final String GEOFENCE_TOPIC = "tnt.realtime.geofence.triggered";
    static final String ACTOR_CONNECTED_TOPIC = "tnt.realtime.actor.connected";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_realtime_outbox_test")
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
    private KafkaRealtimeEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publish(GeofenceTriggerEvent) enqueues to the outbox (PENDING); the poller then "
            + "delivers it to tnt.realtime.geofence.triggered with the raw event JSON")
    void publishGeofenceTriggerGoesThroughOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String tenantId = UUID.randomUUID().toString();
        String actorId = "actor-" + UUID.randomUUID();
        GeofenceZone zone = GeofenceZone.builder()
                .tenantId(tenantId)
                .name("Yaounde Hub")
                .type(GeofenceZoneType.RELAY_HUB)
                .center(GeoCoordinates.of(3.85, 11.55))
                .radiusMeters(500.0)
                .build();
        GeofenceTrigger trigger = GeofenceTrigger.of(
                actorId, tenantId, zone, GeofenceDirection.ENTER,
                GeoCoordinates.of(3.85, 11.55), "mission-1");
        GeofenceTriggerEvent event = new GeofenceTriggerEvent(tenantId, trigger);

        publisher.publish(event).block(Duration.ofSeconds(10));

        // 1. Durably persisted PENDING — nothing on Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(actorId, "Actor", tenantId))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(GEOFENCE_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.getEventId());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "realtime-outbox-geofence-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, GEOFENCE_TOPIC);

            // 2. One poll cycle relays it.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, GEOFENCE_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(GEOFENCE_TOPIC);
            assertThat(received.key()).isEqualTo(event.getEventId());

            // Wire format: the raw domain event JSON, unchanged from the pre-migration
            // kafkaTemplate.send(topic, eventId, objectMapper.writeValueAsString(event)).
            JsonNode wirePayload = objectMapper.readTree(received.value());
            assertThat(wirePayload.get("trigger").get("actorId").asText()).isEqualTo(actorId);
            assertThat(wirePayload.get("trigger").get("direction").asText()).isEqualTo("ENTER");
        }

        // 3. Envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(actorId, "Actor", tenantId))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publish(ActorConnectedEvent) is NOT enqueued to the outbox — it still goes "
            + "straight to Kafka via KafkaTemplate, unchanged from before this migration")
    void publishActorConnectedStaysOnDirectKafkaSend() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        String userId = "user-" + UUID.randomUUID();
        ActorConnectedEvent event = new ActorConnectedEvent(
                tenantId, "session-1", userId, DeviceType.ANDROID, "10.0.0.1");

        publisher.publish(event).block(Duration.ofSeconds(10));

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "realtime-direct-connected-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, ACTOR_CONNECTED_TOPIC);

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, ACTOR_CONNECTED_TOPIC, Duration.ofSeconds(15));
            assertThat(received.topic()).isEqualTo(ACTOR_CONNECTED_TOPIC);
            assertThat(received.key()).isEqualTo(event.getEventId());
        }

        // No outbox envelope was created for this event type.
        StepVerifier.create(envelopeRepository.findByAggregateId(userId, "Actor", tenantId))
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

        @Bean(name = {"realtimeKafkaTemplate", "tntKafkaTemplate"})
        KafkaTemplate<String, String> tntKafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> configs = new HashMap<>();
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configs);
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        KafkaRealtimeEventPublisher kafkaRealtimeEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("realtimeKafkaTemplate") KafkaTemplate<String, String> realtimeKafkaTemplate,
                @Qualifier("tntObjectMapper") ObjectMapper tntObjectMapper) {
            return new KafkaRealtimeEventPublisher(publishEventUseCase, realtimeKafkaTemplate, tntObjectMapper);
        }
    }
}
