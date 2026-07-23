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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Phase 0 stop-gap on Link's {@code /nearby} endpoints
 * (audit n6 S25, Chantier G — see docs/audits/remediation/phase-0-critical.md).
 *
 * <p>Verifies that {@link NetworkAlertR2dbcRepository#findByTenantIdAndStatusWithinBoundingBox}
 * is bounded at the SQL level itself (a hard {@code LIMIT} in the query, not an in-memory
 * filter applied afterward): seeding more active alerts than the cap inside the requested
 * bounding box must still yield at most {@link NetworkAlertR2dbcRepository#MAX_NEARBY_RESULTS} rows.
 *
 * @author Dilane PAFE
 */
@Testcontainers
@Tag("integration")
class NetworkAlertR2dbcRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("tnt_link_alert_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static NetworkAlertR2dbcRepository repository;
    private static DatabaseClient databaseClient;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Configuration
    @EnableR2dbcRepositories(basePackageClasses = NetworkAlertR2dbcRepository.class)
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

        databaseClient.sql("CREATE SCHEMA IF NOT EXISTS tnt_link;").then().block();
        databaseClient.sql("""
                CREATE TABLE IF NOT EXISTS tnt_link.network_alerts (
                    id UUID PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    reporter_id UUID NOT NULL,
                    alert_type VARCHAR(40) NOT NULL,
                    description VARCHAR(1000),
                    latitude DOUBLE PRECISION NOT NULL,
                    longitude DOUBLE PRECISION NOT NULL,
                    severity VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    confirm_count INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL,
                    resolved_at TIMESTAMPTZ,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """).then().block();

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("connectionFactory", ConnectionFactory.class, () -> connectionFactory);
        context.register(TestR2dbcConfig.class);
        context.refresh();

        repository = context.getBean(NetworkAlertR2dbcRepository.class);
    }

    @Test
    @DisplayName("findByTenantIdAndStatusWithinBoundingBox() is capped at MAX_NEARBY_RESULTS even when far more rows match")
    void findWithinBoundingBoxIsBoundedBySqlLimit() {
        int seededInsideBox = NetworkAlertR2dbcRepository.MAX_NEARBY_RESULTS + 50;

        StepVerifier.create(insertAlerts(seededInsideBox, 1.0, 10.0, "ACTIVE")).verifyComplete();
        // Noise: outside the box, and inside the box but RESOLVED — neither should count toward the cap check.
        StepVerifier.create(insertAlerts(5, 50.0, 50.0, "ACTIVE")).verifyComplete();
        StepVerifier.create(insertAlerts(5, 1.0, 10.0, "RESOLVED")).verifyComplete();

        StepVerifier.create(
                repository.findByTenantIdAndStatusWithinBoundingBox(TENANT_ID, "ACTIVE", 0.0, 2.0, 9.0, 11.0)
                        .collectList()
        ).assertNext(results -> {
            assertThat(results).hasSize(NetworkAlertR2dbcRepository.MAX_NEARBY_RESULTS);
            assertThat(results).allSatisfy(alert -> {
                assertThat(alert.getStatus()).isEqualTo("ACTIVE");
                assertThat(alert.getLatitude()).isBetween(0.0, 2.0);
                assertThat(alert.getLongitude()).isBetween(9.0, 11.0);
            });
        }).verifyComplete();
    }

    private Mono<Void> insertAlerts(int count, double baseLat, double baseLng, String status) {
        Mono<Void> chain = Mono.empty();
        for (int i = 0; i < count; i++) {
            double lat = baseLat + (i % 10) * 0.001;
            double lng = baseLng + (i % 10) * 0.001;
            chain = chain.then(databaseClient.sql("""
                    INSERT INTO tnt_link.network_alerts
                        (id, tenant_id, reporter_id, alert_type, description, latitude, longitude,
                         severity, status, confirm_count, created_at, updated_at)
                    VALUES (:id, :tenantId, :reporterId, 'POTHOLE', 'seed', :lat, :lng,
                            'LOW', :status, 0, now(), now())
                    """)
                    .bind("id", UUID.randomUUID())
                    .bind("tenantId", TENANT_ID)
                    .bind("reporterId", UUID.randomUUID())
                    .bind("lat", lat)
                    .bind("lng", lng)
                    .bind("status", status)
                    .then());
        }
        return chain;
    }
}
