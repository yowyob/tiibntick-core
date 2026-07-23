package com.yowyob.tiibntick.core.geo.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxPollerPort;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.tiibntick.core.geo.domain.event.RoadNodeCreatedEvent;
import com.yowyob.tiibntick.core.geo.domain.event.TrafficConditionChangedEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

/**
 * End-to-end integration test proving the Chantier C · Audit n°3 · P5 migration:
 * {@link KafkaGeoEventPublisher} now enqueues events into the {@code yow-event-kernel}
 * transactional outbox instead of sending to Kafka directly, and {@code OutboxPollerService}
 * relays them asynchronously — same topics, same raw-domain-event-JSON wire format, and same
 * tenant-id record key existing consumers (tnt-route-core, tnt-actor-core, tnt-delivery-core)
 * already expect.
 *
 * <p>Follows the same template as {@code tnt-delivery-core}'s
 * {@code KafkaDeliveryEventPublisherOutboxIntegrationTest}: real PostgreSQL Testcontainer
 * (event_bus schema only, via yow-event-kernel's own Liquibase changelog) plus an embedded
 * Kafka broker.
 *
 * Author: MANFOUO Braun
 */
@SpringBootTest(classes = KafkaGeoEventPublisherOutboxIntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        KafkaGeoEventPublisherOutboxIntegrationTest.NODE_TOPIC,
        KafkaGeoEventPublisherOutboxIntegrationTest.TRAFFIC_TOPIC,
        KafkaGeoEventPublisherOutboxIntegrationTest.ALERT_TOPIC})
@Tag("integration")
class KafkaGeoEventPublisherOutboxIntegrationTest {

    static final String NODE_TOPIC = "tnt.geo.node.events";
    static final String TRAFFIC_TOPIC = "tnt.geo.traffic.events";
    static final String ALERT_TOPIC = "tnt.geo.alert.created";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_geo_outbox_test")
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
    private KafkaGeoEventPublisher publisher;

    @Autowired
    private OutboxPollerPort outboxPollerPort;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    @DisplayName("publishRoadNodeCreated() enqueues to the outbox (PENDING); the poller then "
            + "delivers it to the node topic with the raw event JSON and tenant-id key")
    void publishRoadNodeCreatedGoesThroughOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        UUID tenantId = UUID.randomUUID();
        String nodeId = "node-" + UUID.randomUUID();
        RoadNodeCreatedEvent event = RoadNodeCreatedEvent.of(
                tenantId, nodeId, "HUB", 3.848, 11.502, "Yaounde Hub", "YDE");

        publisher.publishRoadNodeCreated(event).block(Duration.ofSeconds(10));

        // 1. Durably persisted PENDING — nothing on Kafka yet.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        nodeId, "RoadNode", tenantId.toString()))
                .assertNext(envelope -> {
                    assertThat(envelope.getKafkaTopic()).isEqualTo(NODE_TOPIC);
                    assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(envelope.getCorrelationId()).isEqualTo(event.eventId().toString());
                    // Pre-migration key contract: tenant id, not aggregate id.
                    assertThat(envelope.getKafkaPartitionKey()).isEqualTo(tenantId.toString());
                })
                .verifyComplete();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps(embeddedKafka, "geo-outbox-node-it", true));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> rawConsumer = new KafkaConsumer<>(consumerProps)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(rawConsumer, NODE_TOPIC);

            // 2. One poll cycle relays it.
            StepVerifier.create(outboxPollerPort.poll())
                    .expectNextMatches(count -> count >= 1)
                    .verifyComplete();

            ConsumerRecord<String, String> received =
                    getSingleRecord(rawConsumer, NODE_TOPIC, Duration.ofSeconds(15));

            assertThat(received.topic()).isEqualTo(NODE_TOPIC);
            assertThat(received.key()).isEqualTo(tenantId.toString());

            // Wire format: the raw domain event JSON, unchanged from the pre-migration
            // kafkaTemplate.send(topic, tenantId, objectMapper.writeValueAsString(event)).
            JsonNode wirePayload = objectMapper.readTree(received.value());
            assertThat(wirePayload.get("nodeId").asText()).isEqualTo(nodeId);
            assertThat(wirePayload.get("tenantId").asText()).isEqualTo(tenantId.toString());
            assertThat(wirePayload.get("nodeType").asText()).isEqualTo("HUB");
        }

        // 3. Envelope transitions to PUBLISHED.
        StepVerifier.create(envelopeRepository.findByAggregateId(
                        nodeId, "RoadNode", tenantId.toString()))
                .assertNext(envelope -> assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED))
                .verifyComplete();
    }

    @Test
    @DisplayName("publishTrafficChanged() routes to both the traffic topic and the alert topic "
            + "through the outbox — every call already represents a significant change")
    void publishTrafficChangedRoutesToTrafficAndAlertTopics() {
        UUID tenantId = UUID.randomUUID();
        String arcId = "arc-" + UUID.randomUUID();
        TrafficConditionChangedEvent event =
                TrafficConditionChangedEvent.of(tenantId, arcId, 1.0, 1.5);

        publisher.publishTrafficChanged(event).block(Duration.ofSeconds(10));

        List<com.yowyob.kernel.event.domain.model.DomainEventEnvelope> envelopes =
                envelopeRepository.findByAggregateId(arcId, "RoadArc", tenantId.toString())
                        .collectList().block(Duration.ofSeconds(10));
        assertThat(envelopes).hasSize(2);
        assertThat(envelopes).extracting(
                com.yowyob.kernel.event.domain.model.DomainEventEnvelope::getKafkaTopic)
                .containsExactlyInAnyOrder(TRAFFIC_TOPIC, ALERT_TOPIC);
        assertThat(envelopes).allSatisfy(envelope ->
                assertThat(envelope.getStatus()).isEqualTo(EnvelopeStatus.PENDING));
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

        @Bean(name = "geoObjectMapper")
        ObjectMapper geoObjectMapper() {
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
        KafkaGeoEventPublisher kafkaGeoEventPublisher(
                PublishEventUseCase publishEventUseCase,
                @Qualifier("geoObjectMapper") ObjectMapper geoObjectMapper) {
            return new KafkaGeoEventPublisher(publishEventUseCase, geoObjectMapper);
        }
    }
}
