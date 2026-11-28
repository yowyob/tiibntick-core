package com.yowyob.tiibntick.core.administration.adapter.out.persistence;

import com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter.DeliveryThresholdsRepositoryAdapter;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.PutOrganizationCommand;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.UpdatePlatformCommand;
import com.yowyob.tiibntick.core.administration.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.administration.application.service.DeliveryThresholdsService;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import com.yowyob.tiibntick.core.administration.support.PostgisTestDatabase;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Delivery thresholds on a real PostGIS migrated by the real {@code tnt-administration-master.yaml}: seeded platform
 * row, R2DBC round trip of every column, optimistic locking, CHECK constraints, idempotent re-run of the changesets,
 * and one transaction for the row and its event (a failing event publication rolls the row back).
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Delivery thresholds — persistence and transaction on PostGIS")
class DeliveryThresholdsPersistenceIntegrationTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    static PostgisTestDatabase db;
    static DeliveryThresholdsRepositoryAdapter repository;
    static AnnotationConfigApplicationContext context;
    static final AtomicBoolean FAIL_EVENTS = new AtomicBoolean();

    @BeforeAll
    static void migrate() throws Exception {
        db = PostgisTestDatabase.start();
        db.migrate();
        db.migrate(); // idempotent re-run: every changeset already applied, nothing fails
        repository = new DeliveryThresholdsRepositoryAdapter(DatabaseClient.create(db.connectionFactory()));
        context = new AnnotationConfigApplicationContext(TxConfig.class);
    }

    @AfterAll
    static void stop() throws Exception {
        if (context != null) {
            context.close();
        }
        if (db != null) {
            db.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("the changesets seed the platform row with the decided values, version 0, relay bounds 3..30 (D-130)")
    void seededPlatformRow() {
        StepVerifier.create(repository.findPlatform())
                .assertNext(p -> {
                    assertThat(p.values()).isEqualTo(DeliveryThresholdValues.defaults());
                    assertThat(p.independentRelayStorage()).isEqualTo(RelayStorageBounds.DEFAULT);
                    assertThat(p.version()).isZero();
                    assertThat(p.updatedBy()).isNull();
                    assertThat(p.updatedAt()).isNotNull();
                }).verifyComplete();
    }

    @Test
    @Order(2)
    @DisplayName("platform: every column round-trips, version increments, a stale version updates nothing")
    void platformRoundTripAndLock() {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThreshold.class);
        int i = 0;
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            map.put(t, t == RELAY_STORAGE_DAYS ? 20 : t == RELAY_STORAGE_REMINDER_DAYS_BEFORE ? 2 : t.min() + 1 + i++ % 3);
        }
        DeliveryThresholdValues values = DeliveryThresholdValues.of(map);
        PlatformDeliveryThresholds next = new PlatformDeliveryThresholds(values, new RelayStorageBounds(4, 12),
                ADMIN, Instant.parse("2026-11-26T09:00:00Z"), 1).validate();

        StepVerifier.create(repository.updatePlatform(next, 0L))
                .assertNext(saved -> {
                    assertThat(saved.values()).isEqualTo(values);
                    assertThat(saved.independentRelayStorage()).isEqualTo(new RelayStorageBounds(4, 12));
                    assertThat(saved.version()).isEqualTo(1L);
                    assertThat(saved.updatedBy()).isEqualTo(ADMIN);
                    assertThat(saved.updatedAt()).isEqualTo(Instant.parse("2026-11-26T09:00:00Z"));
                }).verifyComplete();
        StepVerifier.create(repository.updatePlatform(next, 0L)).verifyComplete(); // stale: empty
        StepVerifier.create(repository.findPlatform())
                .assertNext(p -> assertThat(p.values()).isEqualTo(values)).verifyComplete();

        // back to the defaults
        StepVerifier.create(repository.updatePlatform(new PlatformDeliveryThresholds(DeliveryThresholdValues.defaults(),
                        RelayStorageBounds.DEFAULT, ADMIN, Instant.now(), 2), 1L))
                .assertNext(p -> {
                    assertThat(p.version()).isEqualTo(2L);
                    assertThat(p.independentRelayStorage()).isEqualTo(RelayStorageBounds.DEFAULT);
                }).verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("CHECK constraints: single PLATFORM row, ranges, reminder < storage, relay bounds ordered")
    void checkConstraints() {
        assertThatThrownBy(() -> db.execute(
                "INSERT INTO administration.tnt_delivery_thresholds (id) VALUES ('OTHER')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("singleton");
        assertThatThrownBy(() -> db.execute(
                "UPDATE administration.tnt_delivery_thresholds SET max_delivery_attempts = 11"))
                .isInstanceOf(SQLException.class).hasMessageContaining("attempts");
        assertThatThrownBy(() -> db.execute(
                "UPDATE administration.tnt_delivery_thresholds SET relay_storage_reminder_days_before = 7"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reminder");
        assertThatThrownBy(() -> db.execute("UPDATE administration.tnt_delivery_thresholds SET "
                + "independent_relay_storage_min_days = 9, independent_relay_storage_max_days = 3"))
                .isInstanceOf(SQLException.class).hasMessageContaining("relay_bounds");
        // D-130: the bounds are always set
        assertThatThrownBy(() -> db.execute("UPDATE administration.tnt_delivery_thresholds SET "
                + "independent_relay_storage_min_days = NULL"))
                .isInstanceOf(SQLException.class).hasMessageContaining("null");
        assertThatThrownBy(() -> db.execute("INSERT INTO administration.tnt_organization_delivery_thresholds "
                + "(organization_id, relay_storage_days, relay_storage_reminder_days_before) VALUES ('"
                + UUID.randomUUID() + "', 3, 3)"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reminder_storage");
        assertThatThrownBy(() -> db.execute("INSERT INTO administration.tnt_organization_delivery_thresholds "
                + "(organization_id, prolonged_stop_minutes) VALUES ('" + UUID.randomUUID() + "', 0)"))
                .isInstanceOf(SQLException.class).hasMessageContaining("stop");
    }

    @Test
    @Order(4)
    @DisplayName("organization: insert once (nulls = inherit), update with version, stale refused, list, delete")
    void organizationOverrides() {
        UUID org = UUID.randomUUID();
        OrganizationDeliveryThresholds created = new OrganizationDeliveryThresholds(org,
                DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5, GPS_SIGNAL_LOST_MINUTES, 25)), ADMIN,
                Instant.parse("2026-11-26T10:00:00Z"), 0);
        StepVerifier.create(repository.insertOrganization(created))
                .assertNext(o -> {
                    assertThat(o.values()).isEqualTo(created.values());
                    assertThat(o.values().isSet(RELAY_STORAGE_DAYS)).isFalse();
                    assertThat(o.version()).isZero();
                }).verifyComplete();
        StepVerifier.create(repository.insertOrganization(created)).verifyComplete(); // already exists: empty

        OrganizationDeliveryThresholds changed = new OrganizationDeliveryThresholds(org,
                DeliveryThresholdValues.of(Map.of(RELAY_STORAGE_DAYS, 10)), ADMIN, Instant.now(), 1);
        StepVerifier.create(repository.updateOrganization(changed, 0L))
                .assertNext(o -> {
                    assertThat(o.version()).isEqualTo(1L);
                    assertThat(o.values()).isEqualTo(changed.values()); // previous values replaced, not merged
                }).verifyComplete();
        StepVerifier.create(repository.updateOrganization(changed, 0L)).verifyComplete();
        StepVerifier.create(repository.findAllOrganizations().filter(o -> o.organizationId().equals(org)).count())
                .expectNext(1L).verifyComplete();
        StepVerifier.create(repository.deleteOrganization(org)).expectNext(true).verifyComplete();
        StepVerifier.create(repository.deleteOrganization(org)).expectNext(false).verifyComplete();
        StepVerifier.create(repository.findOrganization(org)).verifyComplete();
    }

    @Test
    @Order(5)
    @DisplayName("one transaction: row and event stored together; a failing event rolls the row back")
    void rowAndEventInOneTransaction() throws Exception {
        ManageDeliveryThresholdsUseCase service = context.getBean(ManageDeliveryThresholdsUseCase.class);
        long version = repository.findPlatform().map(PlatformDeliveryThresholds::version).block();
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThresholdValues.defaults().asMap());
        map.put(MAX_DELIVERY_ATTEMPTS, 6);

        FAIL_EVENTS.set(true);
        StepVerifier.create(service.updatePlatform(new UpdatePlatformCommand(DeliveryThresholdValues.of(map),
                RelayStorageBounds.DEFAULT, version, ADMIN, TENANT))).expectError(IllegalStateException.class).verify();
        assertThat(count("SELECT count(*) FROM it_events")).isZero();
        assertThat(count("SELECT max_delivery_attempts FROM administration.tnt_delivery_thresholds")).isEqualTo(3);
        assertThat(count("SELECT version FROM administration.tnt_delivery_thresholds")).isEqualTo(version);

        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                DeliveryThresholdValues.of(Map.of(MAX_FAILED_RETURNS, 2)), null, ADMIN, TENANT)))
                .expectError(IllegalStateException.class).verify();
        assertThat(count("SELECT count(*) FROM administration.tnt_organization_delivery_thresholds "
                + "WHERE organization_id = '" + ORG + "'")).isZero();

        FAIL_EVENTS.set(false);
        StepVerifier.create(service.updatePlatform(new UpdatePlatformCommand(DeliveryThresholdValues.of(map),
                        RelayStorageBounds.DEFAULT, version, ADMIN, TENANT)))
                .assertNext(p -> assertThat(p.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(6)).verifyComplete();
        assertThat(count("SELECT max_delivery_attempts FROM administration.tnt_delivery_thresholds")).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM it_events WHERE event_type = 'DeliveryThresholdsChanged'"))
                .isEqualTo(1);
    }

    @Test
    @Order(6)
    @DisplayName("upgrade (evt-l1-003, D-130): NULL bounds become 3..30, a bound already set by the admin is kept")
    void upgradeFillsDefaultBounds() throws Exception {
        try (PostgisTestDatabase old = PostgisTestDatabase.start()) {
            old.migrateAllBut(1); // the database as it was before evt-l1-003
            old.execute("UPDATE administration.tnt_delivery_thresholds SET independent_relay_storage_max_days = 20");
            old.migrate();
            try (Connection c = old.jdbc(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT independent_relay_storage_min_days, independent_relay_storage_max_days "
                            + "FROM administration.tnt_delivery_thresholds")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(3);
                assertThat(rs.getInt(2)).isEqualTo(20);
            }
        }
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = db.jdbc(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** The real service with @Transactional, an R2DBC transaction manager and an event store written in the same tx. */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TxConfig {

        @Bean
        ConnectionFactory connectionFactory() {
            return db.connectionFactory();
        }

        @Bean
        ReactiveTransactionManager transactionManager(ConnectionFactory cf) {
            return new R2dbcTransactionManager(cf);
        }

        @Bean
        DatabaseClient databaseClient(ConnectionFactory cf) {
            return DatabaseClient.create(cf);
        }

        @Bean
        DeliveryThresholdsRepositoryAdapter deliveryThresholdsRepository(DatabaseClient client) {
            return new DeliveryThresholdsRepositoryAdapter(client);
        }

        @Bean
        AgencyOrganizationPort agencyOrganizationPort() {
            return organizationId -> Mono.just(true);
        }

        /** Stands for the outbox: the envelope row is written in the caller's transaction, then may fail. */
        @Bean
        TntAdministrationEventPublisher eventPublisher(DatabaseClient client) throws SQLException {
            db.execute("CREATE TABLE IF NOT EXISTS it_events (id BIGSERIAL PRIMARY KEY, event_type TEXT, aggregate_id UUID)");
            return new TntAdministrationEventPublisher() {
                @Override
                public Mono<Void> publish(UUID tenantId, String eventType, String module, UUID aggregateId,
                                          Map<String, Object> payload) {
                    return client.sql("INSERT INTO it_events (event_type, aggregate_id) VALUES (:t, :a)")
                            .bind("t", eventType).bind("a", aggregateId).fetch().rowsUpdated()
                            .then(Mono.defer(() -> FAIL_EVENTS.get()
                                    ? Mono.error(new IllegalStateException("outbox down"))
                                    : Mono.empty()));
                }

                @Override
                public Mono<Void> publish(String topic, UUID tenantId, Map<String, Object> payload) {
                    return Mono.empty();
                }
            };
        }

        @Bean
        DeliveryThresholdsService deliveryThresholdsService(DeliveryThresholdsRepositoryAdapter repository,
                                                            AgencyOrganizationPort agencies,
                                                            TntAdministrationEventPublisher events) {
            return new DeliveryThresholdsService(repository, agencies, events);
        }
    }
}
