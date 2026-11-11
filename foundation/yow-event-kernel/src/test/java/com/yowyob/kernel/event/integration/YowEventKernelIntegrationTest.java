package com.yowyob.kernel.event.integration;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.in.QueryEventUseCase;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test proving that {@code yow-event-kernel} is actually
 * wired and functional as a Spring Boot module (Chantier C · Audit n°3 · P1-P4,
 * see {@code docs/audits/remediation/phase-0-critical.md}).
 *
 * <p>Loads a minimal Spring context ({@link YowEventKernelTestConfig}) that
 * activates {@code YowEventKernelAutoConfiguration} exactly the way
 * {@code tnt-bootstrap} does, against real PostgreSQL, Kafka and Redis
 * Testcontainers instances (no in-memory fakes) — this is the module's own
 * {@code event_bus} schema, migrated via its real
 * {@code db/changelog/yow-event-kernel-master.yaml} changelog, exactly as
 * {@code tnt-bootstrap} would run it.
 *
 * <p>The test publishes a {@link DomainEventEnvelope} through
 * {@link PublishEventUseCase} (the module's public inbound port) and then reads
 * it back through {@link QueryEventUseCase} (a second, independent port) —
 * proving the full round trip: envelope + outbox row both actually persisted in
 * {@code event_bus.*} and re-readable, which is exactly what was previously
 * impossible (no autoconfiguration import, no component scan, no repository
 * implementation, no schema).
 */
@SpringBootTest(classes = YowEventKernelTestConfig.class)
@Testcontainers
@Tag("integration")
class YowEventKernelIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yow_event_kernel_test")
            .withUsername("yow_test")
            .withPassword("yow_test_secret");

    /**
     * A real single-node Kafka (KRaft). The host port is chosen up front and announced as the advertised
     * listener, so the producer can reach the broker through the mapped port.
     */
    static final int KAFKA_PORT = freePort();

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> KAFKA = new GenericContainer<>(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,CONTROLLER://:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:" + KAFKA_PORT)
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                    .withPortBindings(new PortBinding(Ports.Binding.bindPort(KAFKA_PORT), new ExposedPort(9092))))
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

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

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:" + KAFKA_PORT);

        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired
    private PublishEventUseCase publishEventUseCase;

    @Autowired
    private QueryEventUseCase queryEventUseCase;

    @Autowired
    private EventEnvelopeRepository envelopeRepository;

    @Autowired
    private OutboxEntryRepository outboxEntryRepository;

    private static final String TENANT_ID = "tenant-event-kernel-it";

    @Test
    @DisplayName("publish() persists the envelope in event_bus.domain_event_envelopes and it is readable back")
    void publishedEnvelopeIsPersistedAndReadableFromEventBus() {
        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                .correlationId(UUID.randomUUID().toString())
                .eventType("IntegrationTestEvent")
                .aggregateId("aggregate-" + UUID.randomUUID())
                .aggregateType("IntegrationTestAggregate")
                .tenantId(TENANT_ID)
                .solutionCode("TNT")
                .payload("{\"hello\":\"world\"}")
                .kafkaTopic("tnt.event-kernel.integration-test")
                .build();

        StepVerifier.create(
                publishEventUseCase.publish(envelope)
                        .then(queryEventUseCase.findById(envelope.getId(), TENANT_ID))
        )
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(envelope.getId());
                    // stored, then sent to the real broker: the transactional outbox is gone (2026-09-18), so PUBLISHED directly
                    assertThat(found.getStatus()).isEqualTo(EnvelopeStatus.PUBLISHED);
                    assertThat(found.getPayload()).isEqualTo("{\"hello\":\"world\"}");
                    assertThat(found.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(found.getPayloadHash()).isEqualTo(envelope.getPayloadHash());
                })
                .verifyComplete();

        // Second, independent read path: directly through the repository port (not just the use-case service).
        StepVerifier.create(envelopeRepository.findById(envelope.getId(), TENANT_ID))
                .assertNext(found -> assertThat(found.getEventType()).isEqualTo("IntegrationTestEvent"))
                .verifyComplete();

        // The message really reached the broker, with the standard Yowyob headers.
        try (var consumer = newConsumer()) {
            consumer.subscribe(java.util.List.of("tnt.event-kernel.integration-test"));
            var seen = new java.util.ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            long deadline = System.currentTimeMillis() + 20_000;
            while (seen.stream().noneMatch(r -> "{\"hello\":\"world\"}".equals(r.value())) && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
            }
            var record = seen.stream().filter(r -> "{\"hello\":\"world\"}".equals(r.value())).findFirst();
            assertThat(record).as("message consumed from Kafka").isPresent();
            assertThat(new String(record.get().headers().lastHeader("X-Yow-Tenant-Id").value())).isEqualTo(TENANT_ID);
            assertThat(new String(record.get().headers().lastHeader("X-Yow-Envelope-Id").value())).isEqualTo(envelope.getId().value());
        }
    }

    private static org.apache.kafka.clients.consumer.KafkaConsumer<String, String> newConsumer() {
        var props = new java.util.Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + KAFKA_PORT);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        return new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
    }

    @Test
    @DisplayName("findByCorrelationId() and findByAggregateId() find the same published envelope")
    void publishedEnvelopeIsFindableByCorrelationAndAggregate() {
        String correlationId = UUID.randomUUID().toString();
        String aggregateId = "aggregate-" + UUID.randomUUID();

        DomainEventEnvelope envelope = DomainEventEnvelope.wrap()
                .correlationId(correlationId)
                .eventType("IntegrationTestEvent")
                .aggregateId(aggregateId)
                .aggregateType("IntegrationTestAggregate")
                .tenantId(TENANT_ID)
                .solutionCode("TNT")
                .payload("{\"n\":1}")
                .kafkaTopic("tnt.event-kernel.integration-test")
                .build();

        StepVerifier.create(publishEventUseCase.publish(envelope)).verifyComplete();

        StepVerifier.create(queryEventUseCase.findByCorrelationId(correlationId, TENANT_ID))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(queryEventUseCase.findByAggregateId(
                        aggregateId, "IntegrationTestAggregate", TENANT_ID))
                .expectNextCount(1)
                .verifyComplete();
    }
}
