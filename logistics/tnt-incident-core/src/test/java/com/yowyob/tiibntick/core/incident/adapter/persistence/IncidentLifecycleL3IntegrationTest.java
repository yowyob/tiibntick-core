package com.yowyob.tiibntick.core.incident.adapter.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.common.kafka.TntEventMasking;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.incident.adapter.kafka.IncidentAggregateEventOutboxPublisher;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentBlockchainR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentCooperationR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentDriverReplacementR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentEventLogR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentEvidenceR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.IncidentR2dbcRepository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.ParcelIncidentLinkR2dbcRepository;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
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
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L3 on a real PostgreSQL and a real Kafka broker: the incident's new columns round-trip, every save publishes its
 * transitions on {@code tnt.incident.events} (key = incident, versions 1..n in order), the automatic confirmation
 * query finds expired proposals, and changesets {@code evt-l3-001/002} turn a legacy DETECTED row into a blocking
 * REPORTED one.
 */
@SpringBootTest(classes = IncidentLifecycleL3IntegrationTest.TestConfig.class)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = TntTopics.INCIDENT_EVENTS)
@Tag("integration")
class IncidentLifecycleL3IntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_incident_l3_test").withUsername("tnt_test").withPassword("tnt_test_secret");

    static final UUID LEGACY_ID = UUID.randomUUID();

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection conn = jdbc()) {
            update(conn, "db/changelog/yow-event-kernel-master.yaml", new LabelExpression());
            // the schema as before lot L3, with a legacy row opened by the removed detection listener
            update(conn, "db/changelog/tnt-incident-master.xml", new LabelExpression("!evt-l3"));
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO tnt_incidents (id, reference_code, tenant_id, agency_id, source_platform,"
                        + " mission_id, category, type, severity, status, description, reported_at, version)"
                        + " VALUES ('" + LEGACY_ID + "', 'TNT-INC-000001', '" + UUID.randomUUID() + "', '"
                        + UUID.randomUUID() + "', 'AGENCY', '" + UUID.randomUUID() + "', 'GEOGRAPHIC',"
                        + " 'GEO_ROAD_FLOODED', 'MEDIUM', 'DETECTED', 'legacy', now(), 0)");
            }
            update(conn, "db/changelog/tnt-incident-master.xml", new LabelExpression());
        }
    }

    private static Connection jdbc() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void update(Connection conn, String changelog, LabelExpression labels) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        new Liquibase(changelog, new ClassLoaderResourceAccessor(), database).update(new Contexts(), labels);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired IncidentPersistenceAdapter repository;
    @Autowired EmbeddedKafkaBroker kafka;

    @Test
    @DisplayName("legacy DETECTED row became REPORTED and blocking (evt-l3-001/002)")
    void legacyRowMigrated() throws Exception {
        try (Connection conn = jdbc(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT status, blocking, event_sequence FROM tnt_incidents WHERE id = '"
                     + LEGACY_ID + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("REPORTED");
            assertThat(rs.getBoolean("blocking")).isTrue();
            assertThat(rs.getLong("event_sequence")).isZero();
        }
        Incident legacy = repository.findById(LEGACY_ID).block();
        assertThat(legacy.getStatus()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(legacy.isBlockingMission()).isTrue();
    }

    @Test
    @DisplayName("a lifecycle saved step by step publishes ordered, keyed events and round-trips the L3 columns")
    void lifecyclePublishesOrderedEvents() throws Exception {
        UUID courier = UUID.randomUUID();
        Incident reported = Incident.create(UUID.randomUUID(), UUID.randomUUID(), PlatformType.AGENCY,
                UUID.randomUUID(), IncidentCategory.GEOGRAPHIC, IncidentType.GEO_ROAD_FLOODED, "route coupée", courier,
                ActorRole.AGENCY_DRIVER, List.of(UUID.randomUUID()));
        Incident saved = repository.save(reported.forceBlockingByCourier()).block();
        saved = repository.save(saved.acknowledge().triage(IncidentSeverity.MEDIUM, null, null, true)).block();
        saved = repository.save(saved.pendingAgencyAssignment().startAgencyHandling()).block();
        repository.save(saved.proposeResolution(IncidentDeliveryOutcome.FAILED,
                IncidentResponsibleParty.RECIPIENT, "destinataire absent",
                TntEventActor.of(TntEventActor.Type.AGENCY, UUID.randomUUID()))).block();

        Incident loaded = repository.findById(reported.getId()).block();
        assertThat(loaded.isBlocking()).isTrue();
        assertThat(loaded.isBlockingForcedByCourier()).isTrue();
        assertThat(loaded.getDeliveryOutcome()).isEqualTo(IncidentDeliveryOutcome.FAILED);
        assertThat(loaded.getResponsibleParty()).isEqualTo(IncidentResponsibleParty.RECIPIENT);
        assertThat(loaded.getResolutionNote()).isEqualTo("destinataire absent");
        assertThat(loaded.getResolutionProposedAt()).isNotNull();
        assertThat(loaded.getEventSequence()).isEqualTo(6);
        assertThat(loaded.getPendingTransitions()).isEmpty();
        assertThat(loaded.getReferenceCode()).matches("TNT-INC-\\d{6}-[0-9A-F]{10}");

        // QO-14: found by the automatic confirmation query once the delay is over, not before
        assertThat(repository.findConfirmationExpired(Instant.now().minus(Duration.ofHours(1)), 10)
                .map(Incident::getId).collectList().block()).doesNotContain(reported.getId());
        assertThat(repository.findConfirmationExpired(Instant.now().plus(Duration.ofHours(1)), 10)
                .map(Incident::getId).collectList().block()).contains(reported.getId());

        saved = repository.save(loaded.confirmResolution(null, true)).block();
        repository.save(saved.close()).block();

        List<ConsumerRecord<String, String>> records = consume(reported.getId().toString(), 8);
        ObjectMapper json = new ObjectMapper();
        List<String> types = new ArrayList<>();
        long expectedVersion = 1;
        for (ConsumerRecord<String, String> r : records) {
            JsonNode node = json.readTree(r.value());
            assertThat(r.key()).isEqualTo(reported.getId().toString());
            assertThat(node.path("aggregateVersion").asLong()).isEqualTo(expectedVersion++);
            assertThat(r.value()).doesNotContain("route coupée");
            types.add(node.path("eventType").asText());
        }
        assertThat(types).containsExactly("IncidentReported", "IncidentAcknowledged", "IncidentTriaged",
                "IncidentPendingAgencyAssignment", "IncidentAgencyHandling", "IncidentPendingConfirmation",
                "IncidentResolved", "IncidentClosed");
        JsonNode resolved = json.readTree(records.get(6).value());
        assertThat(resolved.path("trigger").asText()).isEqualTo("CONFIRMATION_TIMED_OUT");
        assertThat(resolved.path("details").path("deliveryOutcome").asText()).isEqualTo("FAILED");
        assertThat(resolved.path("payload").path("status").asText()).isEqualTo("RESOLVED");
    }

    private List<ConsumerRecord<String, String>> consume(String key, int expected) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(kafka, "incident-l3-it-" + key, true));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            kafka.consumeFromAnEmbeddedTopic(consumer, TntTopics.INCIDENT_EVENTS);
            long deadline = System.currentTimeMillis() + 30_000;
            while (out.size() < expected && System.currentTimeMillis() < deadline) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).forEach(r -> {
                    if (key.equals(r.key())) {
                        out.add(r);
                    }
                });
            }
        }
        return out;
    }

    @Configuration
    @Import({R2dbcAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class,
            DataR2dbcAutoConfiguration.class, DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class, YowEventKernelAutoConfiguration.class})
    @EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.incident.adapter.persistence.repository")
    static class TestConfig {

        @Bean
        MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        @Primary
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
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
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
        }

        @Bean
        IncidentMapper incidentMapper(ObjectMapper objectMapper) {
            return new IncidentMapper(objectMapper);
        }

        @Bean
        IncidentAggregateEventOutboxPublisher incidentAggregatePublisher(PublishEventBatchUseCase batch,
                                                                         ObjectProvider<TntEventMasking> masking) {
            return new IncidentAggregateEventOutboxPublisher(batch, masking);
        }

        @Bean
        IncidentPersistenceAdapter incidentPersistenceAdapter(IncidentR2dbcRepository incidents,
                                                              IncidentEventLogR2dbcRepository logs,
                                                              IncidentEvidenceR2dbcRepository evidence,
                                                              IncidentBlockchainR2dbcRepository chain,
                                                              IncidentDriverReplacementR2dbcRepository replacements,
                                                              IncidentCooperationR2dbcRepository cooperation,
                                                              ParcelIncidentLinkR2dbcRepository links,
                                                              IncidentMapper mapper,
                                                              IncidentAggregateEventOutboxPublisher publisher) {
            IncidentPersistenceAdapter adapter = new IncidentPersistenceAdapter(incidents, logs, evidence, chain,
                    replacements, cooperation, links, mapper);
            adapter.setAggregatePublisher(publisher);
            return adapter;
        }
    }
}
