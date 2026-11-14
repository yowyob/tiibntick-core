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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrade gate for the Go delivery need → announcement link column: a database already migrated by
 * the previous version (every changeset except the labelled one), holding announcement rows,
 * receives the new changeset on restart — additive, validated, idempotent, existing rows kept.
 */
@Testcontainers
@Tag("integration")
class GoNeedAnnouncementMigrationIntegrationTest {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";
    private static final String NEW_CHANGESET_LABEL = "go-need-announcement";

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

    private static long linkColumns(Connection conn) throws Exception {
        return count(conn, "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'announcements' AND column_name = 'delivery_need_id'");
    }

    /** Minimal announcement row: every NOT NULL column without a default gets a neutral value. */
    private static void insertAnnouncement(Connection conn, UUID id, String extraColumn, Object extraValue) throws Exception {
        List<String> cols = new ArrayList<>(List.of("id"));
        List<String> vals = new ArrayList<>(List.of("'" + id + "'"));
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'announcements' "
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
            s.execute("INSERT INTO announcements (" + String.join(",", cols) + ") VALUES (" + String.join(",", vals) + ")");
        }
    }

    @Test
    @DisplayName("upgrade: a database migrated by the previous version gets the link column, rows kept, one announcement per need")
    void upgradeFromPreviousVersion() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            // 1. the production database as the previous version left it
            Liquibase previous = liquibase(conn);
            previous.update(new Contexts(), new LabelExpression("!" + NEW_CHANGESET_LABEL));
            assertThat(linkColumns(conn)).as("previous version: no link column").isZero();
            assertThat(previous.listUnrunChangeSets(new Contexts(), new LabelExpression())).hasSize(1);
            conn.setAutoCommit(true); // Liquibase leaves the connection with autoCommit=false
            insertAnnouncement(conn, UUID.randomUUID(), null, null);
            insertAnnouncement(conn, UUID.randomUUID(), null, null);
            long before = count(conn, "SELECT count(*) FROM announcements");
            long appliedBefore = count(conn, "SELECT count(*) FROM databasechangelog");

            // 2. the application restarts on the new version
            Liquibase upgrade = liquibase(conn);
            upgrade.validate();
            upgrade.update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true);

            assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
            assertThat(linkColumns(conn)).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM announcements")).as("existing rows kept").isEqualTo(before);
            assertThat(count(conn, "SELECT count(*) FROM announcements WHERE delivery_need_id IS NULL"))
                    .as("existing announcements are not linked to any need").isEqualTo(before);

            // 3. at most one announcement per need (NULLs stay free)
            UUID need = UUID.randomUUID();
            insertAnnouncement(conn, UUID.randomUUID(), "delivery_need_id", need);
            assertThatThrownBy(() -> insertAnnouncement(conn, UUID.randomUUID(), "delivery_need_id", need))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_announcements_delivery_need_id");

            // 4. a further restart applies nothing
            Liquibase again = liquibase(conn);
            again.validate();
            again.update(new Contexts(), new LabelExpression());
            assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
        }
    }
}
