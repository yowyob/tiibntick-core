package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1: the deliveries carrying a parcel, whatever their tenant, most recent first — the query a relay
 * deposit (keyed on the parcel id) uses to find its delivery. Real Postgres, real module changelog.
 */
@Testcontainers
@Tag("integration")
class DeliveriesByParcelQueryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_delivery_by_parcel_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    static final UUID PARCEL = UUID.randomUUID();
    static final UUID OTHER_PARCEL = UUID.randomUUID();
    static final UUID OLDER = UUID.randomUUID();
    static final UUID NEWER_OTHER_TENANT = UUID.randomUUID();
    static final UUID UNRELATED = UUID.randomUUID();

    static R2dbcDeliveryRepository repository;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-delivery-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true);
            parcel(conn, PARCEL);
            parcel(conn, OTHER_PARCEL);
            delivery(conn, OLDER, UUID.randomUUID(), PARCEL, Instant.parse("2026-11-01T10:00:00Z"));
            delivery(conn, NEWER_OTHER_TENANT, UUID.randomUUID(), PARCEL, Instant.parse("2026-11-02T10:00:00Z"));
            delivery(conn, UNRELATED, UUID.randomUUID(), OTHER_PARCEL, Instant.parse("2026-11-03T10:00:00Z"));
        }
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        repository = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(new PostgresqlConnectionFactory(config)))
                .getRepository(R2dbcDeliveryRepository.class);
    }

    private static void parcel(Connection conn, UUID id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, created_at, updated_at)
                VALUES (?, 1, 10, 10, 10, now(), now())""")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static void delivery(Connection conn, UUID id, UUID tenant, UUID parcel, Instant createdAt) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tnt_deliveries (id, tenant_id, parcel_id, sender_id, status, urgency, pickup_city,
                                            delivery_city, recipient_name, recipient_phone, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'AT_RELAY_POINT', 'STANDARD', 'Douala', 'Douala', 'R', '+237600000000', ?, ?)""")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setObject(3, parcel);
            ps.setObject(4, UUID.randomUUID());
            ps.setObject(5, java.sql.Timestamp.from(createdAt));
            ps.setObject(6, java.sql.Timestamp.from(createdAt));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("every delivery carrying the parcel, across tenants, most recent first; other parcels excluded")
    void byParcel() {
        assertThat(repository.findByParcelIdNoTenant(PARCEL).map(DeliveryEntity::getId).collectList().block())
                .containsExactly(NEWER_OTHER_TENANT, OLDER);
        assertThat(repository.findByParcelIdNoTenant(UUID.randomUUID()).collectList().block()).isEmpty();
    }
}
