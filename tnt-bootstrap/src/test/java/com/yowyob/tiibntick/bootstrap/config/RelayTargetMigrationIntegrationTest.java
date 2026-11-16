package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade gate for the destination relay point of a Go delivery need (lot C-48): a database already
 * migrated by the previous version (every changeset except the labelled one), holding delivery needs,
 * receives the new changeset on restart — additive, validated, idempotent, existing rows kept and
 * left without a relay point.
 */
@Testcontainers
@Tag("integration")
class RelayTargetMigrationIntegrationTest {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";
    private static final String NEW_CHANGESET_LABEL = "relay-target";

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

    private static Liquibase liquibase(Connection conn) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(conn));
        ResourceAccessor accessor = new CompositeResourceAccessor(
                new DirectoryResourceAccessor(Path.of("src/main/resources")),
                new ClassLoaderResourceAccessor());
        return new Liquibase(MASTER, accessor, database);
    }

    private static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long newColumns(Connection conn) throws Exception {
        return count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_name = 'delivery_needs' "
                + "AND column_name IN ('target_relay_point_id', 'requested_storage_days')");
    }

    /** Minimal delivery need row: every NOT NULL column without a default gets a neutral value; FKs off. */
    private static void insertNeed(Connection conn, UUID id, String extraColumn, Object extraValue) throws Exception {
        List<String> cols = new ArrayList<>(List.of("id"));
        List<String> vals = new ArrayList<>(List.of("'" + id + "'"));
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'delivery_needs' "
                        + "AND is_nullable = 'NO' AND column_default IS NULL AND column_name <> 'id'")) {
            while (rs.next()) {
                cols.add(rs.getString(1));
                vals.add(switch (rs.getString(2)) {
                    case "uuid" -> "'" + UUID.randomUUID() + "'";
                    case "boolean" -> "false";
                    case "integer", "bigint", "smallint", "numeric", "double precision", "real" -> "0";
                    case "timestamp without time zone", "timestamp with time zone", "date" -> "now()";
                    default -> "'x'";
                });
            }
        }
        if (extraColumn != null) {
            cols.add(extraColumn);
            vals.add("'" + extraValue + "'");
        }
        try (Statement s = conn.createStatement()) {
            s.execute("SET session_replication_role = replica"); // no FK checks for these synthetic rows
            s.execute("INSERT INTO delivery_needs (" + String.join(",", cols) + ") VALUES (" + String.join(",", vals) + ")");
            s.execute("SET session_replication_role = DEFAULT");
        }
    }

    @Test
    @DisplayName("upgrade: a database migrated by the previous version gets the relay point columns, rows kept")
    void upgradeFromPreviousVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // 1. the production database as the previous version left it
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + NEW_CHANGESET_LABEL));
            assertThat(newColumns(conn)).as("previous version: no relay point column").isZero();
            assertThat(previous.listUnrunChangeSets(new Contexts(), new LabelExpression())).hasSize(1);
            conn.setAutoCommit(true);
            insertNeed(conn, UUID.randomUUID(), null, null);
            insertNeed(conn, UUID.randomUUID(), null, null);
            long before = count(conn, "SELECT count(*) FROM delivery_needs");
            long appliedBefore = count(conn, "SELECT count(*) FROM databasechangelog");

            // 2. the application restarts on the new version
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true);

            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
            assertThat(newColumns(conn)).isEqualTo(2);
            assertThat(count(conn, "SELECT count(*) FROM delivery_needs")).as("existing rows kept").isEqualTo(before);
            assertThat(count(conn, "SELECT count(*) FROM delivery_needs WHERE target_relay_point_id IS NULL"))
                    .as("existing needs have no relay point").isEqualTo(before);

            // 3. a need can now carry its relay point, found through the index
            UUID relay = UUID.randomUUID();
            insertNeed(conn, UUID.randomUUID(), "target_relay_point_id", relay);
            assertThat(count(conn, "SELECT count(*) FROM delivery_needs WHERE target_relay_point_id = '" + relay + "'"))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_delivery_needs_target_relay_point_id'"))
                    .isEqualTo(1);

            // 4. a further restart applies nothing
            Liquibase again = liquibase(conn);
            again.validate();
            again.update(new Contexts(), new LabelExpression());
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
        }
    }
}
