package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Phase 0 stop-gap on Link's {@code /nearby} endpoints
 * (audit n6 S25, Chantier G — see docs/audits/remediation/phase-0-critical.md).
 *
 * <p>Verifies that {@link NetworkNodeR2dbcRepository#findWithinBoundingBox} is bounded
 * at the SQL level itself (a hard {@code LIMIT} in the query, not an in-memory filter
 * applied afterward): seeding more rows than the cap inside the requested bounding box
 * must still yield at most {@link NetworkNodeR2dbcRepository#MAX_NEARBY_RESULTS} rows.
 *
 * <p>Uses the real generated Spring Data R2DBC repository proxy (not a hand-rolled
 * stand-in) against a Testcontainers PostgreSQL instance, so the actual {@code @Query}
 * SQL is what gets exercised.
 *
 * @author Dilane PAFE
 */
@Testcontainers
@Tag("integration")
class NetworkNodeR2dbcRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tnt_link_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static NetworkNodeR2dbcRepository repository;
    private static DatabaseClient databaseClient;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Configuration
    @EnableR2dbcRepositories(basePackageClasses = NetworkNodeR2dbcRepository.class)
    static class TestR2dbcConfig {
        @Bean
        R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
            return new R2dbcEntityTemplate(connectionFactory);
        }
    }

    @BeforeAll
    static void setUpAll() {
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        databaseClient = DatabaseClient.create(connectionFactory);

        databaseClient.sql("""
                CREATE SCHEMA IF NOT EXISTS tnt_link;
                """).then().block();
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS tnt_link.network_nodes (
                    id UUID PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    ref_type VARCHAR(30) NOT NULL,
                    ref_id UUID NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    trust_score DOUBLE PRECISION NOT NULL DEFAULT 0,
                    gamification_level INTEGER NOT NULL DEFAULT 1,
                    community_score DOUBLE PRECISION NOT NULL DEFAULT 0,
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    heading DOUBLE PRECISION,
                    description VARCHAR(500),
                    declared_zone_name VARCHAR(200),
                    declared_city VARCHAR(200),
                    declared_capacity_parcels INTEGER,
                    badges VARCHAR(500) NOT NULL DEFAULT '',
                    last_zone_id UUID,
                    zone_transition_count INTEGER NOT NULL DEFAULT 0,
                    pol_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    pol_peer_count INTEGER NOT NULL DEFAULT 0,
                    pol_verified_at TIMESTAMPTZ,
                    did_identifier VARCHAR(100),
                    did_issuer VARCHAR(100),
                    did_verified_at TIMESTAMPTZ,
                    beacon_active BOOLEAN NOT NULL DEFAULT FALSE,
                    beacon_message VARCHAR(500),
                    beacon_expires_at TIMESTAMPTZ,
                    beacon_radius_km DOUBLE PRECISION,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """).then().block();

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("connectionFactory", ConnectionFactory.class, () -> connectionFactory);
        context.register(TestR2dbcConfig.class);
        context.refresh();

        repository = context.getBean(NetworkNodeR2dbcRepository.class);
    }

    @Test
    @DisplayName("findWithinBoundingBox() is capped at MAX_NEARBY_RESULTS even when far more rows match")
    void findWithinBoundingBoxIsBoundedBySqlLimit() {
        int seededInsideBox = NetworkNodeR2dbcRepository.MAX_NEARBY_RESULTS + 50;

        // Seed more matching rows than the cap, all inside the requested bounding box.
        StepVerifier.create(insertNodes(seededInsideBox, 1.0, 10.0)).verifyComplete();
        // Plus a few rows clearly outside the box, to prove the WHERE clause itself still applies
        // alongside the LIMIT (not just an arbitrary truncation of an unrelated result set).
        StepVerifier.create(insertNodes(5, 50.0, 50.0)).verifyComplete();

        StepVerifier.create(
                repository.findWithinBoundingBox(TENANT_ID, 0.0, 2.0, 9.0, 11.0).collectList()
        ).assertNext(results -> {
            assertThat(results).hasSize(NetworkNodeR2dbcRepository.MAX_NEARBY_RESULTS);
            assertThat(results).allSatisfy(node -> {
                assertThat(node.getLatitude()).isBetween(0.0, 2.0);
                assertThat(node.getLongitude()).isBetween(9.0, 11.0);
            });
        }).verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertNodes(int count, double baseLat, double baseLng) {
        reactor.core.publisher.Mono<Void> chain = reactor.core.publisher.Mono.empty();
        for (int i = 0; i < count; i++) {
            double lat = baseLat + (i % 10) * 0.001;
            double lng = baseLng + (i % 10) * 0.001;
            chain = chain.then(databaseClient.sql("""
                    INSERT INTO tnt_link.network_nodes
                        (id, tenant_id, ref_type, ref_id, status, latitude, longitude, created_at, updated_at)
                    VALUES (:id, :tenantId, 'AGENCY', :refId, 'ONLINE', :lat, :lng, now(), now())
                    """)
                    .bind("id", UUID.randomUUID())
                    .bind("tenantId", TENANT_ID)
                    .bind("refId", UUID.randomUUID())
                    .bind("lat", lat)
                    .bind("lng", lng)
                    .then());
        }
        return chain;
    }
}
