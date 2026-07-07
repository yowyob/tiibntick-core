package com.yowyob.tiibntick.core.sync.adapter.out.persistence;

import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for R2dbcEntityVersionRepository using Testcontainers PostgreSQL.
 * Verifies upsert, findCurrent, findChangedSince, and countChangedSince.
 *
 * Tagged @Tag("integration") — runs separately via Failsafe plugin.
 *
 * Author: MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class R2dbcEntityVersionRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgis/postgis:16-3.4")
            .withDatabaseName("tnt_sync_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private R2dbcEntityVersionRepository repository;
    private DatabaseClient databaseClient;

    private static final String TENANT_ID = "tenant-integration-test";

    @BeforeEach
    void setUp() {
        io.r2dbc.postgresql.PostgresqlConnectionConfiguration config =
                io.r2dbc.postgresql.PostgresqlConnectionConfiguration.builder()
                        .host(POSTGRES.getHost())
                        .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                        .database(POSTGRES.getDatabaseName())
                        .username(POSTGRES.getUsername())
                        .password(POSTGRES.getPassword())
                        .build();

        ConnectionFactory connectionFactory = new io.r2dbc.postgresql.PostgresqlConnectionFactory(config);
        R2dbcEntityTemplate template = new R2dbcEntityTemplate(connectionFactory);
        databaseClient = DatabaseClient.create(connectionFactory);

        repository = new R2dbcEntityVersionRepository(template, databaseClient);

        // Create schema
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS tnt_entity_version (
                    id BIGSERIAL,
                    tenant_id VARCHAR(255) NOT NULL,
                    aggregate_type VARCHAR(100) NOT NULL,
                    aggregate_id VARCHAR(255) NOT NULL,
                    version BIGINT NOT NULL,
                    operation VARCHAR(20) NOT NULL,
                    payload_json TEXT,
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_by_user_id VARCHAR(255) DEFAULT 'system',
                    CONSTRAINT pk_entity_version PRIMARY KEY (tenant_id, aggregate_type, aggregate_id)
                )
                """)
                .then()
                .block();

        // Clean test data
        databaseClient.sql("DELETE FROM tnt_entity_version WHERE tenant_id = :tenantId")
                .bind("tenantId", TENANT_ID)
                .then()
                .block();
    }

    private EntityVersionRecord buildRecord(String aggregateType, String aggregateId, DeltaOperation op) {
        return new EntityVersionRecord(
                TENANT_ID, aggregateType, aggregateId,
                System.currentTimeMillis(), op,
                "{\"id\":\"" + aggregateId + "\"}",
                LocalDateTime.now(), "test-user");
    }

    @Test
    @DisplayName("upsert() inserts a new record successfully")
    void upsertInsertsRecord() {
        EntityVersionRecord record = buildRecord("MISSION", "M-001", DeltaOperation.CREATED);

        StepVerifier.create(
                repository.upsert(record)
                        .then(repository.findCurrent(TENANT_ID, "MISSION", "M-001"))
        )
        .assertNext(found -> {
            assertThat(found.aggregateType()).isEqualTo("MISSION");
            assertThat(found.aggregateId()).isEqualTo("M-001");
            assertThat(found.tenantId()).isEqualTo(TENANT_ID);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("upsert() updates existing record on conflict (ON CONFLICT DO UPDATE)")
    void upsertUpdatesExistingRecord() {
        EntityVersionRecord initial = buildRecord("MISSION", "M-002", DeltaOperation.CREATED);
        EntityVersionRecord updated = new EntityVersionRecord(
                TENANT_ID, "MISSION", "M-002",
                System.currentTimeMillis() + 1000,
                DeltaOperation.STATUS_CHANGED, "{\"status\":\"PICKED_UP\"}",
                LocalDateTime.now(), "dispatcher");

        StepVerifier.create(
                repository.upsert(initial)
                        .then(repository.upsert(updated))
                        .then(repository.findCurrent(TENANT_ID, "MISSION", "M-002"))
        )
        .assertNext(found -> {
            assertThat(found.operation()).isEqualTo(DeltaOperation.STATUS_CHANGED);
            assertThat(found.payloadJson()).contains("PICKED_UP");
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("findCurrent() returns empty Mono for non-existent aggregate")
    void findCurrentReturnsEmptyForMissing() {
        StepVerifier.create(repository.findCurrent(TENANT_ID, "PACKAGE", "PKG-GHOST"))
                .verifyComplete();
    }

    @Test
    @DisplayName("findChangedSince() returns only records after the cutoff time")
    void findChangedSinceReturnsCorrectSubset() {
        LocalDateTime beforeInsert = LocalDateTime.now().minusSeconds(1);

        EntityVersionRecord r1 = buildRecord("MISSION", "M-010", DeltaOperation.CREATED);
        EntityVersionRecord r2 = buildRecord("PACKAGE", "PKG-010", DeltaOperation.UPDATED);

        StepVerifier.create(
                repository.upsert(r1)
                        .then(repository.upsert(r2))
                        .thenMany(repository.findChangedSince(TENANT_ID, beforeInsert, null, 100))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    @DisplayName("findChangedSince() filters by aggregate type when filter is provided")
    void findChangedSinceFiltersAggregateTypes() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        StepVerifier.create(
                repository.upsert(buildRecord("MISSION", "M-020", DeltaOperation.CREATED))
                        .then(repository.upsert(buildRecord("PACKAGE", "PKG-020", DeltaOperation.CREATED)))
                        .then(repository.upsert(buildRecord("RELAY_HUB", "HUB-020", DeltaOperation.UPDATED)))
                        .thenMany(repository.findChangedSince(TENANT_ID, before, Set.of("MISSION", "PACKAGE"), 100))
        )
        .expectNextCount(2)
        .verifyComplete();
    }

    @Test
    @DisplayName("countChangedSince() returns correct count")
    void countChangedSinceReturnsCorrectCount() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        StepVerifier.create(
                repository.upsert(buildRecord("MISSION", "M-030", DeltaOperation.CREATED))
                        .then(repository.upsert(buildRecord("MISSION", "M-031", DeltaOperation.UPDATED)))
                        .then(repository.countChangedSince(TENANT_ID, before))
        )
        .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(2))
        .verifyComplete();
    }
}
