package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-readiness check of the whole schema, using the real aggregated changelog
 * ({@code db/changelog/tnt-core-master.yaml}, every module JAR on the classpath) against a real
 * PostGIS Postgres:
 * <ol>
 *   <li><b>fresh install</b> — the master applies cleanly on an empty database, validates, and a
 *       second run is a no-op (no checksum drift, nothing re-applied);</li>
 *   <li><b>upgrade of a populated database</b> — everything before the Kernel wallet migration is
 *       applied and filled with legacy ledger rows, then the remaining changesets are applied,
 *       exactly as a production database would be upgraded: the legacy tables disappear, the new
 *       ones appear, the delivery receive-code columns are added, and the schema is stable again.</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@Testcontainers
@Tag("integration")
class LiquibaseMasterChangelogIntegrationTest {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";
    private static final String GUEST_RELOCATION_CHANGESET = "075-relocate-guest-person";
    /** The legacy fixed guest id, matched by pattern so the literal is not written in the project. */
    private static final String LEGACY_GUEST_ID_PATTERN = "'^0{8}-0{4}-0{4}-0{4}-0{11}1$'";
    private static final String FIRST_WALLET_MIGRATION_CHANGESET = "004-create-wallet-settlements-and-holds";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    private static String createDatabase() throws Exception {
        String name = "tnt_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    private static Connection open(String url) throws Exception {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Liquibase liquibase(Connection conn) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(conn));
        // When failsafe runs against the Spring Boot fat jar, tnt-bootstrap's own resources sit in
        // BOOT-INF/classes and are invisible to the classloader: read the master and the bootstrap-owned
        // changes from src/main/resources, the module changelogs from their JARs. When it runs against
        // target/classes (integration-tests profile), the classloader already sees them: adding the
        // source directory would make Liquibase find every bootstrap changelog twice.
        boolean bootstrapResourcesOnClasspath =
                LiquibaseMasterChangelogIntegrationTest.class.getClassLoader().getResource(MASTER) != null;
        ResourceAccessor accessor = bootstrapResourcesOnClasspath
                ? new ClassLoaderResourceAccessor()
                : new CompositeResourceAccessor(
                        new DirectoryResourceAccessor(Path.of("src/main/resources")),
                        new ClassLoaderResourceAccessor());
        return new Liquibase(MASTER, accessor, database);
    }

    private static Set<String> tables(Connection conn, String schema) throws Exception {
        Set<String> out = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = '"
                     + schema + "'")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("fresh install: the whole master applies, validates, and a second run changes nothing")
    void freshInstallIsCleanAndIdempotent() throws Exception {
        try (Connection conn = open(createDatabase())) {
            Liquibase lb = liquibase(conn);
            lb.validate();
            lb.update(new Contexts(), new LabelExpression());

            assertThat(lb.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
            long applied = count(conn, "SELECT count(*) FROM databasechangelog");
            assertThat(applied).isGreaterThan(100);

            // a restart of the application re-runs the master: nothing may be applied again
            Liquibase again = liquibase(conn);
            again.validate();
            again.update(new Contexts(), new LabelExpression());
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(applied);

            // lot L5: the legacy intents table (005) is replaced by the escrow intents of the same name, the local
            // holds are dropped (decision H.1)
            assertThat(tables(conn, "billing"))
                    .contains("wallet_settlements", "wallet_compensations", "wallet_payment_intents",
                            "wallet_escrows", "wallet_cash_collections", "wallet_debts")
                    .doesNotContain("wallet_wallets", "wallet_transactions", "wallet_holds",
                            "wallet_payment_splits", "wallet_reconciliations");
            // lot L6: correspondences with the Kernel books; the local journal stays (read-only, QO-21)
            assertThat(tables(conn, "accounting"))
                    .contains("kernel_account_links", "kernel_journal_links", "kernel_entry_links", "journal_entries");
        }
    }

    @Test
    @DisplayName("upgrade: a database holding legacy ledger rows is migrated by the remaining changesets")
    void upgradeOfPopulatedDatabase() throws Exception {
        try (Connection conn = open(createDatabase())) {
            Liquibase lb = liquibase(conn);
            List<ChangeSet> all = lb.getDatabaseChangeLog().getChangeSets();
            int cut = -1;
            for (int i = 0; i < all.size(); i++) {
                if (FIRST_WALLET_MIGRATION_CHANGESET.equals(all.get(i).getId())) {
                    cut = i;
                    break;
                }
            }
            assertThat(cut).as("wallet migration changeset present in the master").isPositive();

            // 1. the production database as it was before the Kernel wallet migration
            lb.update(cut, "");
            assertThat(tables(conn, "billing")).contains("wallet_wallets", "wallet_transactions")
                    .doesNotContain("wallet_settlements", "wallet_holds");
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO billing.wallet_wallets (id, user_id, tenant_id, balance) VALUES ('"
                        + UUID.randomUUID() + "','" + UUID.randomUUID() + "','" + UUID.randomUUID() + "', 1500.00)");
            }
            assertThat(count(conn, "SELECT count(*) FROM billing.wallet_wallets")).isEqualTo(1);

            // 2. the application restarts on the new version
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression());

            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
            assertThat(tables(conn, "billing"))
                    .contains("wallet_settlements", "wallet_compensations", "wallet_payment_intents",
                            "wallet_escrows", "wallet_cash_collections", "wallet_debts")
                    .doesNotContain("wallet_wallets", "wallet_transactions", "wallet_holds",
                            "wallet_payment_splits", "wallet_reconciliations");
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE column_name = 'receive_code' AND table_name = 'deliveries'"))
                    .as("receive-code columns added by changeset 071, dropped by evt-l5-001 (lot L5, H.1)")
                    .isZero();
        }
    }

    @Test
    @DisplayName("corrective migration: the Guest person leaves its legacy fixed id and every reference follows")
    void guestPersonIsRelocated() throws Exception {
        try (Connection conn = open(createDatabase())) {
            Liquibase lb = liquibase(conn);
            List<ChangeSet> all = lb.getDatabaseChangeLog().getChangeSets();
            int cut = -1;
            for (int i = 0; i < all.size(); i++) {
                if (GUEST_RELOCATION_CHANGESET.equals(all.get(i).getId())) {
                    cut = i;
                    break;
                }
            }
            assertThat(cut).as("guest relocation changeset present in the master").isPositive();

            // 1. production as it is today: the guest person sits under the legacy id
            lb.update(cut, "");
            assertThat(count(conn, "SELECT count(*) FROM persons WHERE id::text ~ " + LEGACY_GUEST_ID_PATTERN))
                    .as("guest person under the legacy id before the migration").isEqualTo(1);
            long fksBefore = count(conn, "SELECT count(*) FROM pg_constraint "
                    + "WHERE contype = 'f' AND confrelid = 'persons'::regclass");
            // changeset c36-in-app-notifications (lot C-36 of main) drops this one on purpose during the upgrade
            long droppedByC36 = count(conn, "SELECT count(*) FROM pg_constraint "
                    + "WHERE contype = 'f' AND confrelid = 'persons'::regclass AND conname = 'fk_notification_person'");
            assertThat(count(conn, "SELECT count(*) FROM clients c JOIN persons p ON p.id = c.person_id "
                    + "WHERE p.email = 'guest@tiibntick.com'")).isEqualTo(1);

            // 2. the application restarts on the new version (closure gdd-final excluded: it drops persons, and is
            //    covered by GddFinalClosureMigrationIntegrationTest)
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final"))).isEmpty();

            assertThat(count(conn, "SELECT count(*) FROM persons WHERE id::text ~ " + LEGACY_GUEST_ID_PATTERN))
                    .as("no person left under the legacy id").isZero();
            assertThat(count(conn, "SELECT count(*) FROM persons WHERE email = 'guest@tiibntick.com'"))
                    .as("the guest person still exists, once").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM clients c JOIN persons p ON p.id = c.person_id "
                    + "WHERE p.email = 'guest@tiibntick.com'"))
                    .as("the guest client follows its person").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM pg_constraint "
                    + "WHERE contype = 'f' AND confrelid = 'persons'::regclass"))
                    .as("foreign keys on persons(id) re-created").isEqualTo(fksBefore - droppedByC36);
            assertThat(count(conn, "SELECT count(*) FROM pg_constraint "
                    + "WHERE contype = 'f' AND confrelid = 'persons'::regclass AND NOT convalidated"))
                    .isZero();
        }
    }
}
