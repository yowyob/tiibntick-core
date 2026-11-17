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
 * Upgrade gate for the opening hours of a relay point (lot C-49): a database migrated by the previous
 * version has no {@code opening_hours} table (its old script was never included); one created by hand
 * from that script has a mandatory {@code logistics_id}. In both cases the new changeset leaves a table
 * the {@code OpeningHours} entity can write — additive, validated, idempotent, existing rows kept.
 */
@Testcontainers
@Tag("integration")
class RelayOpeningHoursMigrationIntegrationTest {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";
    private static final String NEW_CHANGESET_LABEL = "relay-opening-hours";

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

    private static void exec(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    /** Liquibase of the previous version, then the new one; returns the changelog rows before the upgrade. */
    private static long previousVersion(Connection conn) throws Exception {
        Liquibase previous = liquibase(conn);
        previous.update(new Contexts(), new LabelExpression("!" + NEW_CHANGESET_LABEL));
        assertThat(previous.listUnrunChangeSets(new Contexts(), new LabelExpression())).hasSize(1);
        conn.setAutoCommit(true);
        return count(conn, "SELECT count(*) FROM databasechangelog");
    }

    private static void upgradeTwice(Connection conn, long appliedBefore) throws Exception {
        Liquibase upgrade = liquibase(conn);
        upgrade.validate();
        upgrade.update(new Contexts(), new LabelExpression());
        conn.setAutoCommit(true);
        assertThat(upgrade.listUnrunChangeSets(new Contexts(), new LabelExpression())).isEmpty();
        assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
        Liquibase again = liquibase(conn);
        again.validate();
        again.update(new Contexts(), new LabelExpression());
        conn.setAutoCommit(true);
        assertThat(count(conn, "SELECT count(*) FROM databasechangelog")).isEqualTo(appliedBefore + 1);
    }

    private static void entityRowCanBeWritten(Connection conn) throws Exception {
        UUID hub = UUID.randomUUID();
        exec(conn, "INSERT INTO opening_hours (id, relay_point_id, day_of_week, open_time, close_time, is_closed) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + hub + "', 'MONDAY', '08:00', '18:00', false)");
        assertThat(count(conn, "SELECT count(*) FROM opening_hours WHERE relay_point_id = '" + hub + "'")).isEqualTo(1);
        assertThat(count(conn, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_opening_hours_relay_point_id'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("upgrade: a database migrated by the previous version gets the opening_hours table the entity reads")
    void upgradeFromPreviousVersion_createsTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            long appliedBefore = previousVersion(conn);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.tables WHERE table_name = 'opening_hours'"))
                    .as("previous version: no opening_hours table").isZero();

            upgradeTwice(conn, appliedBefore);
            entityRowCanBeWritten(conn);
        }
    }

    @Test
    @DisplayName("upgrade: an opening_hours table created from the old script keeps its rows and its logistics_id")
    void upgradeOfLegacyTable_keepsRowsAndColumn() throws Exception {
        try (Connection conn = DriverManager.getConnection(createDatabase(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            long appliedBefore = previousVersion(conn);
            exec(conn, "CREATE TABLE opening_hours (id UUID PRIMARY KEY, logistics_id UUID NOT NULL, "
                    + "day_of_week VARCHAR(20) NOT NULL, open_time TIME, close_time TIME, is_closed BOOLEAN DEFAULT FALSE)");
            exec(conn, "INSERT INTO opening_hours (id, logistics_id, day_of_week) VALUES ('"
                    + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'SUNDAY')");

            upgradeTwice(conn, appliedBefore);

            assertThat(count(conn, "SELECT count(*) FROM opening_hours WHERE day_of_week = 'SUNDAY' AND logistics_id IS NOT NULL"))
                    .as("legacy row and column kept").isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM information_schema.columns WHERE table_name = 'opening_hours' "
                    + "AND column_name = 'logistics_id' AND is_nullable = 'YES'")).isEqualTo(1);
            entityRowCanBeWritten(conn);
        }
    }
}
