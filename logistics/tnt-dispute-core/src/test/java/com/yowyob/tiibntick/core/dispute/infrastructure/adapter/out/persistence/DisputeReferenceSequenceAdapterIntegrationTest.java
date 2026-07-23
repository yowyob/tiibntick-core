package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Postgres integration test for {@link DisputeReferenceSequenceAdapter}.
 *
 * <p>Reproduces, against a real PostgreSQL container, the exact race condition described
 * in Chantier D · Audit n°6 · S1: the old {@code DisputeReference} implementation held a
 * static {@code AtomicInteger} per JVM, so two application <em>instances</em> (or, within a
 * single instance, two concurrent requests each on their own R2DBC connection) generating
 * references at the same time were not actually serialized against each other and could —
 * and, under this exact test with the old code, reliably did — collide.
 *
 * <p>This test simulates that scenario by driving many concurrent {@code nextReference()}
 * calls, each against its own independent {@link ConnectionFactory} (standing in for
 * "another instance's own connection pool", since a single connection pool would already
 * serialize access at the driver level and wouldn't prove anything about cross-instance
 * safety) all pointed at the one shared database — the same topology as two
 * {@code tnt-bootstrap} instances behind a load balancer, both writing to the one Postgres.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class DisputeReferenceSequenceAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_dispute_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @BeforeAll
    static void migrateSchema() throws Exception {
        // Runs the REAL tnt-dispute-core Liquibase changelog — including
        // 009_create_dispute_reference_sequence.sql — against the container, exactly as
        // tnt-bootstrap does at production startup.
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/tnt-dispute-master.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    private DisputeReferenceSequenceAdapter newAdapterWithOwnConnection() {
        final PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        final ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        return new DisputeReferenceSequenceAdapter(DatabaseClient.create(connectionFactory));
    }

    @Test
    @DisplayName("nextReference() never hands out the same reference twice under concurrent, "
            + "multi-connection load (simulating two application instances)")
    void concurrentGenerationNeverCollides() throws InterruptedException {
        final int callers = 20;
        final int perCaller = 25; // 500 references total
        final ExecutorService pool = Executors.newFixedThreadPool(callers);
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger failures = new AtomicInteger();
        final List<List<String>> resultsPerCaller = new java.util.concurrent.CopyOnWriteArrayList<>();

        try {
            final List<java.util.concurrent.Future<List<String>>> futures = IntStream.range(0, callers)
                    .mapToObj(i -> pool.submit(() -> {
                        // Each "caller" gets its own adapter/connection — standing in for a
                        // separate application instance's own connection pool.
                        final DisputeReferenceSequenceAdapter adapter = newAdapterWithOwnConnection();
                        ready.countDown();
                        start.await();
                        return Flux.range(0, perCaller)
                                .flatMap(n -> adapter.nextReference()
                                        .map(DisputeReference::getValue)
                                        .onErrorResume(e -> {
                                            failures.incrementAndGet();
                                            return Mono.empty();
                                        }), 1) // sequential within a caller, concurrent ACROSS callers
                                .collectList()
                                .block();
                    }))
                    .collect(Collectors.toList());

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            for (var f : futures) {
                resultsPerCaller.add(f.get());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures.get()).isZero();

        final List<String> allReferences = resultsPerCaller.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        assertThat(allReferences).hasSize(callers * perCaller);
        assertThat(Set.copyOf(allReferences))
                .as("every reference generated across all concurrent callers must be unique — "
                        + "a duplicate here means two instances handed out the same dispute reference")
                .hasSize(allReferences.size());
    }
}
