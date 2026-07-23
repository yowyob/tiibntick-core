package com.yowyob.tiibntick.bootstrap.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Postgres integration test for {@link TntSchedulerLockConfig}'s
 * {@link LockProvider} bean (Chantier D · Audit n°6 · S2).
 *
 * <p>Reproduces the exact scenario the whole chantier is about: two application
 * instances (here, two independent {@link R2dbcLockProvider}s, each with its own
 * {@link ConnectionFactory}/connection — the same topology as two {@code tnt-bootstrap}
 * instances behind a load balancer) racing to acquire the same named lock against the one
 * shared {@code shedlock} table created by
 * {@code db/changelog/changes/000-create-shedlock-table.sql}. Proves: only one instance
 * ever holds the lock at a time, and the lock becomes available to the other instance
 * again once released.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class TntSchedulerLockConfigIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_shedlock_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/shedlock-test-master.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    private static LockProvider newLockProviderWithOwnConnection() {
        final PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        final ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        return new R2dbcLockProvider(connectionFactory);
    }

    @Test
    @DisplayName("two instances racing for the same named lock: only one holds it at a time")
    void onlyOneInstanceHoldsTheLockAtOnce() {
        final String lockName = "test-job-" + System.nanoTime();
        final LockProvider instanceA = newLockProviderWithOwnConnection();
        final LockProvider instanceB = newLockProviderWithOwnConnection();

        final LockConfiguration config = new LockConfiguration(
                Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);

        // Instance A acquires the lock first.
        final Optional<SimpleLock> lockA = instanceA.lock(config);
        assertThat(lockA).as("instance A should acquire the lock").isPresent();

        // Instance B races for the SAME lock while A still holds it — must be refused.
        final Optional<SimpleLock> lockBWhileHeld = instanceB.lock(config);
        assertThat(lockBWhileHeld)
                .as("instance B must NOT acquire the lock while instance A still holds it — "
                        + "this is exactly the guarantee that prevents two instances from "
                        + "running the same @Scheduled job concurrently")
                .isEmpty();

        // Instance A releases...
        lockA.get().unlock();

        // ...and now instance B can acquire it.
        final Optional<SimpleLock> lockBAfterRelease = instanceB.lock(config);
        assertThat(lockBAfterRelease)
                .as("instance B should acquire the lock once instance A released it")
                .isPresent();
        lockBAfterRelease.get().unlock();
    }
}
