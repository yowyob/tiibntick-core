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
        // failsafe runs after `package`, when tnt-bootstrap's own resources sit inside the Spring Boot
        // fat jar (BOOT-INF/classes) and are invisible to the classloader: read the master and the
        // bootstrap-owned changes from src/main/resources, the module changelogs from their JARs.
        ResourceAccessor accessor = new CompositeResourceAccessor(
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

            assertThat(tables(conn, "billing"))
                    .contains("wallet_settlements", "wallet_holds")
                    .doesNotContain("wallet_wallets", "wallet_transactions", "wallet_payment_intents",
                            "wallet_payment_splits", "wallet_reconciliations");
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
            assertThat(tables(conn, "billing")).contains("wallet_settlements", "wallet_holds")
                    .doesNotContain("wallet_wallets", "wallet_transactions", "wallet_payment_intents",
                            "wallet_payment_splits", "wallet_reconciliations");
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE column_name = 'receive_code' AND table_name = 'deliveries'"))
                    .as("delivery receive-code columns added by changeset 071").isEqualTo(1);
        }
    }
}
