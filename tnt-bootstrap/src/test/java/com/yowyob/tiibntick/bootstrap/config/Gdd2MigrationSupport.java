package com.yowyob.tiibntick.bootstrap.config;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared harness of the lot L2 upgrade tests (same approach as {@code GoNeedAnnouncementMigrationIntegrationTest}). */
final class Gdd2MigrationSupport {

    static final String MASTER = "db/changelog/tnt-core-master.yaml";
    static final String LOT_LABEL = "gdd-l2";

    private Gdd2MigrationSupport() {
    }

    static String createDatabase(PostgreSQLContainer<?> pg) throws Exception {
        String name = "tnt_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return pg.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    static Liquibase liquibase(Connection conn) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        ResourceAccessor accessor = com.yowyob.tiibntick.bootstrap.config.BootstrapChangelogs.accessor();
        return new Liquibase(MASTER, accessor, database);
    }

    static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static String string(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    static Map<String, Object> cols(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /**
     * Inserts a row with the given columns; every other NOT NULL column without a default gets a neutral
     * value of its type. Values are written as SQL literals ({@code null} → NULL, numbers raw, else quoted).
     */
    static void insert(Connection conn, String table, Map<String, Object> given) throws Exception {
        List<String> cols = new ArrayList<>(given.keySet());
        List<String> vals = new ArrayList<>();
        for (Object v : given.values()) vals.add(literal(v));
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = '" + table + "' AND is_nullable = 'NO' AND column_default IS NULL")) {
            while (rs.next()) {
                if (given.containsKey(rs.getString(1))) continue;
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
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO " + table + " (" + String.join(",", cols) + ") VALUES (" + String.join(",", vals) + ")");
        }
    }

    private static String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number) return v.toString();
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
