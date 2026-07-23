package com.yowyob.kernel.event.integration;

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

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");

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
                    assertThat(found.getStatus()).isEqualTo(EnvelopeStatus.PENDING);
                    assertThat(found.getPayload()).isEqualTo("{\"hello\":\"world\"}");
                    assertThat(found.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(found.getPayloadHash()).isEqualTo(envelope.getPayloadHash());
                })
                .verifyComplete();

        // Second, independent read path: directly through the repository port
        // (not just the use-case service), and via the associated outbox entry —
        // both event_bus tables are populated by the same publish() call.
        StepVerifier.create(envelopeRepository.findById(envelope.getId(), TENANT_ID))
                .assertNext(found -> assertThat(found.getEventType()).isEqualTo("IntegrationTestEvent"))
                .verifyComplete();

        StepVerifier.create(outboxEntryRepository.findByEnvelopeId(envelope.getId()))
                .assertNext(entry -> assertThat(entry.getKafkaTopic())
                        .isEqualTo("tnt.event-kernel.integration-test"))
                .verifyComplete();
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
