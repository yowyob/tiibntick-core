package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletHoldR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository.WalletSettlementR2dbcRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.RecordSettlementCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.service.SettlementService;
import com.yowyob.tiibntick.core.billing.wallet.application.service.WalletHoldService;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementMethod;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.SettlementStatus;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres test of the Kernel-migration persistence: the actual {@code tnt-billing-wallet}
 * Liquibase changelog (including {@code 004-create-settlements-and-holds}) and the R2DBC
 * adapters behind {@link SettlementService} / {@link WalletHoldService} — enum and decimal
 * round-trips, the (tenant, type, reference) uniqueness, the derived queries, hold release.
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class WalletSettlementAndHoldRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_wallet_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static SettlementService settlements;
    private static WalletHoldService holds;

    @BeforeAll
    static void migrateAndWire() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-billing-wallet-master.yaml",
                    new ClassLoaderResourceAccessor(), database).update(new Contexts(), new LabelExpression());
        }
        ConnectionFactory cf = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build());
        R2dbcRepositoryFactory factory = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(cf));
        var settlementRepo = new WalletSettlementRepositoryAdapter(factory.getRepository(WalletSettlementR2dbcRepository.class));
        var holdRepo = new WalletHoldRepositoryAdapter(factory.getRepository(WalletHoldR2dbcRepository.class));
        settlements = new SettlementService(settlementRepo);
        holds = new WalletHoldService(holdRepo, settlementRepo);
    }

    private static RecordSettlementCommand cmd(UUID tenant, UUID delivery, UUID payer, UUID payee,
                                               SettlementStatus status) {
        return new RecordSettlementCommand(tenant, "DELIVERY", delivery, payer, payee,
                new BigDecimal("4500.00"), "XAF", SettlementMethod.WALLET_TRANSFER, status,
                "tx-" + delivery, new BigDecimal("0.05"));
    }

    @Test
    @DisplayName("changelog 005 dropped the legacy ledger tables and kept settlements and holds")
    void legacyLedgerTablesAreDropped() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var st = conn.createStatement();
             var rs = st.executeQuery("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = 'billing' AND table_name LIKE 'wallet_%'")) {
            var tables = new java.util.HashSet<String>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertThat(tables).containsExactlyInAnyOrder("wallet_settlements", "wallet_holds");
        }
    }

    @Test
    @DisplayName("a settlement round-trips through Postgres and upserts on (tenant, type, reference): UNPAID → PAID keeps one row")
    void settlementRoundTripAndUpsert() {
        UUID tenant = UUID.randomUUID(), delivery = UUID.randomUUID();
        UUID payer = UUID.randomUUID(), payee = UUID.randomUUID();

        var unpaid = settlements.record(cmd(tenant, delivery, null, payee, SettlementStatus.UNPAID)).block();
        assertThat(unpaid.getCommissionStatus()).isEqualTo(CommissionStatus.NONE);

        var paid = settlements.record(cmd(tenant, delivery, payer, payee, SettlementStatus.PAID)).block();
        assertThat(paid.getId()).isEqualTo(unpaid.getId());

        var read = settlements.find(tenant, "DELIVERY", delivery).block();
        assertThat(read.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(read.getPayerUserId()).isEqualTo(payer);
        assertThat(read.getPayeeUserId()).isEqualTo(payee);
        assertThat(read.getAmount()).isEqualByComparingTo("4500.00");
        assertThat(read.getCommissionAmount()).isEqualByComparingTo("225.00");
        assertThat(read.getCommissionStatus()).isEqualTo(CommissionStatus.DUE);
        assertThat(read.getKernelReference()).isEqualTo("tx-" + delivery);
        assertThat(read.getPaidAt()).isNotNull();

        assertThat(settlements.listCommissionsDue(tenant).collectList().block()).hasSize(1);
        settlements.markCommissionSettled(tenant, read.getId()).block();
        assertThat(settlements.listCommissionsDue(tenant).collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("holdSettlement finds the PAID settlement without a tenant, holds the payee's amount once, then releases it")
    void holdLifecycleOnRealDatabase() {
        UUID tenant = UUID.randomUUID(), delivery = UUID.randomUUID();
        UUID payer = UUID.randomUUID(), payee = UUID.randomUUID();
        settlements.record(cmd(tenant, delivery, payer, payee, SettlementStatus.PAID)).block();

        var h1 = holds.holdSettlement("DELIVERY", delivery, "dispute").block();
        var h2 = holds.holdSettlement("DELIVERY", delivery, "dispute again").block();
        assertThat(h2.getId()).isEqualTo(h1.getId());
        assertThat(holds.activeHeldAmount(payee, "XAF").block()).isEqualByComparingTo("4500.00");
        assertThat(holds.listActive(payee).collectList().block()).hasSize(1);
        assertThat(holds.listActive(payer).collectList().block()).isEmpty();

        assertThat(holds.releaseHolds("DELIVERY", delivery).block()).isEqualTo(1L);
        assertThat(holds.activeHeldAmount(payee, "XAF").block()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("no PAID settlement (unpaid / unknown mission): nothing is held")
    void nothingToHold() {
        UUID tenant = UUID.randomUUID(), delivery = UUID.randomUUID();
        settlements.record(cmd(tenant, delivery, null, UUID.randomUUID(), SettlementStatus.UNPAID)).block();
        assertThat(holds.holdSettlement("DELIVERY", delivery, "x").block()).isNull();
        assertThat(holds.holdSettlement("DELIVERY", UUID.randomUUID(), "x").block()).isNull();
    }
}
