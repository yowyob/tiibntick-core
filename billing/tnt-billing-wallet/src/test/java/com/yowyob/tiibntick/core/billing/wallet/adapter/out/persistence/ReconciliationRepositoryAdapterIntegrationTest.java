package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.mapper.WalletPersistenceMapperImpl;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.ReconciliationStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationId;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.ReconciliationRecord;
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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers Postgres integration test for {@link ReconciliationRepositoryAdapter}.
 *
 * <p>Proves Chantier D · Audit n°6 · S3 is actually fixed: the old
 * {@code InMemoryReconciliationRepository} kept every record in a {@code ConcurrentHashMap}
 * scoped to a single JVM, so a record saved by one application instance was invisible to
 * every other instance (and to the same instance after a restart). This test wires up
 * <em>two independent adapters, each with its own {@link ConnectionFactory}</em> —
 * standing in for two separate application instances — against the one real Postgres
 * container running the actual {@code tnt-billing-wallet} Liquibase changelog, and proves
 * a record written through one is immediately visible through the other.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class ReconciliationRepositoryAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_wallet_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            final Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            final Liquibase liquibase = new Liquibase(
                    "db/changelog/tnt-billing-wallet-master.yaml", new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
        }
    }

    /** Builds a brand-new adapter with its own connection — simulating a separate instance. */
    private ReconciliationRepositoryAdapter newAdapterWithOwnConnection() {
        final PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        final ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(config);
        final R2dbcEntityTemplate template = new R2dbcEntityTemplate(connectionFactory);
        final R2dbcRepositoryFactory repositoryFactory = new R2dbcRepositoryFactory(template);
        final var r2dbcRepository = repositoryFactory.getRepository(
                com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.ReconciliationR2dbcRepository.class);
        return new ReconciliationRepositoryAdapter(r2dbcRepository, new WalletPersistenceMapperImpl());
    }

    private static ReconciliationRecord newRecord(UUID tenantId, YearMonth period) {
        Money total = Money.ofXAF(100_000);
        ReconciliationRecord record = ReconciliationRecord.builder()
                .id(ReconciliationId.generate())
                .tenantId(tenantId)
                .period(period)
                .walletTotal(total)
                .bankStatementTotal(total)
                .discrepancy(Money.zero(total.currency()))
                .status(ReconciliationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        record.evaluate();
        return record;
    }

    @Test
    @DisplayName("a record saved through one instance's connection is immediately visible "
            + "through a second, independent instance's connection — proving state is shared "
            + "via Postgres, not trapped in a per-JVM ConcurrentHashMap")
    void recordSavedByOneInstanceIsVisibleToAnother() {
        final UUID tenantId = UUID.randomUUID();
        final YearMonth period = YearMonth.of(2026, 6);
        final ReconciliationRecord record = newRecord(tenantId, period);

        final ReconciliationRepositoryAdapter instanceA = newAdapterWithOwnConnection();
        final ReconciliationRepositoryAdapter instanceB = newAdapterWithOwnConnection();

        StepVerifier.create(instanceA.save(record))
                .expectNextCount(1)
                .verifyComplete();

        // instanceB never saw instanceA's write except through the shared database.
        StepVerifier.create(instanceB.findByTenantIdAndPeriod(tenantId, period))
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(record.getId());
                    assertThat(found.getTenantId()).isEqualTo(tenantId);
                    assertThat(found.getStatus()).isEqualTo(ReconciliationStatus.BALANCED);
                })
                .verifyComplete();

        // A brand new adapter — standing in for the same instance after a restart, when
        // the old ConcurrentHashMap would have already lost everything — still finds it.
        final ReconciliationRepositoryAdapter afterRestart = newAdapterWithOwnConnection();
        StepVerifier.create(afterRestart.findById(record.getId().value()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("resolveDiscrepancy-style update via one instance is visible to another")
    void updateThroughOneInstanceVisibleToAnother() {
        final UUID tenantId = UUID.randomUUID();
        final YearMonth period = YearMonth.of(2026, 7);
        final ReconciliationRecord record = newRecord(tenantId, period);
        // Force a discrepancy so resolve() is legal.
        ReconciliationRecord withDiscrepancy = ReconciliationRecord.builder()
                .id(record.getId())
                .tenantId(tenantId)
                .period(period)
                .walletTotal(Money.ofXAF(100_000))
                .bankStatementTotal(Money.ofXAF(105_000))
                .discrepancy(Money.ofXAF(5_000))
                .status(ReconciliationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        withDiscrepancy.evaluate();

        final ReconciliationRepositoryAdapter instanceA = newAdapterWithOwnConnection();
        final ReconciliationRepositoryAdapter instanceB = newAdapterWithOwnConnection();

        StepVerifier.create(instanceA.save(withDiscrepancy)).expectNextCount(1).verifyComplete();

        // instanceB loads it, resolves it, and saves the update — a single reactive chain
        // (not a blocking call nested inside another StepVerifier's callback, which risks
        // deadlocking the R2DBC driver's bounded event-loop threads).
        StepVerifier.create(
                        instanceB.findById(record.getId().value())
                                .doOnNext(found -> found.resolve("Bank fee explains the gap"))
                                .flatMap(instanceB::save))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(instanceA.findById(record.getId().value()))
                .assertNext(found -> {
                    assertThat(found.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
                    assertThat(found.getResolutionNote()).isEqualTo("Bank fee explains the gap");
                })
                .verifyComplete();
    }
}
