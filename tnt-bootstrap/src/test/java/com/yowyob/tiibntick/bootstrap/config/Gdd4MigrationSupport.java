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
import org.testcontainers.utility.DockerImageName;

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

/**
 * Shared helpers of the lot L4 data-migration tests ({@code Gdd4*MigrationIntegrationTest}): a fresh database per
 * test, the master changelog applied except the changesets labelled {@code gdd-l4} (the database as the previous
 * version left it), rows inserted into the legacy tables, then the upgrade.
 */
final class Gdd4MigrationSupport {

    static final String MASTER = "db/changelog/tnt-core-master.yaml";
    static final String LOT_LABEL = "gdd-l4";

    private Gdd4MigrationSupport() { }

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> newPostgis() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postgres").withUsername("tnt_test").withPassword("tnt_test_secret");
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

    static void exec(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    /**
     * Inserts a row: the given columns, plus a neutral unique value for every NOT NULL column that has no default,
     * so a legacy table can be filled without knowing its whole history.
     */
    /**
     * Inserts a row into a {@code public} table: the given values, plus a generated value for every other NOT NULL
     * column without default. A missing parent row of any foreign key is created the same way (recursively).
     */
    static void insert(Connection conn, String table, Map<String, Object> given) throws Exception {
        insert(conn, table, given, 0);
    }

    private static void insert(Connection conn, String table, Map<String, Object> given, int depth) throws Exception {
        if (depth > 6) throw new IllegalStateException("foreign key chain too deep at " + table);
        Map<String, String> row = new LinkedHashMap<>();
        given.forEach((k, v) -> row.put(k, literal(v)));
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = '" + table + "' AND is_nullable = 'NO' AND column_default IS NULL")) {
            while (rs.next()) {
                String col = rs.getString(1);
                if (row.containsKey(col)) continue;
                row.put(col, switch (rs.getString(2)) {
                    case "uuid" -> "'" + UUID.randomUUID() + "'";
                    case "boolean" -> "false";
                    case "integer", "bigint", "smallint", "numeric", "double precision", "real" -> "0";
                    case "timestamp without time zone", "timestamp with time zone", "date" -> "now()";
                    default -> "'x" + UUID.randomUUID().toString().substring(0, 8) + "'";
                });
            }
        }
        for (String[] fk : foreignKeys(conn, table)) {
            String value = row.get(fk[0]);
            if (value == null || "NULL".equals(value) || fk[1].equals(table)) continue;
            if (count(conn, "SELECT count(*) FROM " + fk[1] + " WHERE " + fk[2] + " = " + value) == 0) {
                insert(conn, fk[1], Map.of(fk[2], unquote(value)), depth + 1);
            }
        }
        List<String> cols = new ArrayList<>(row.keySet());
        exec(conn, "INSERT INTO " + table + " (" + String.join(",", cols) + ") VALUES ("
                + String.join(",", cols.stream().map(row::get).toList()) + ")");
    }

    /** {column, referenced table, referenced column} of each single-column foreign key of a public table. */
    private static List<String[]> foreignKeys(Connection conn, String table) throws Exception {
        List<String[]> fks = new ArrayList<>();
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT a.attname, cl.relname, af.attname FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                        + "JOIN pg_class cl ON cl.oid = c.confrelid "
                        + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] "
                        + "JOIN pg_attribute af ON af.attrelid = c.confrelid AND af.attnum = c.confkey[1] "
                        + "WHERE c.contype = 'f' AND n.nspname = 'public' AND t.relname = '" + table
                        + "' AND array_length(c.conkey, 1) = 1")) {
            while (rs.next()) fks.add(new String[] {rs.getString(1), rs.getString(2), rs.getString(3)});
        }
        return fks;
    }

    private static Object unquote(String literal) {
        if (literal.startsWith("'") && literal.endsWith("'")) {
            return literal.substring(1, literal.length() - 1).replace("''", "'");
        }
        return new Object() {
            @Override
            public String toString() {
                return literal;
            }
        };
    }

    private static String literal(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
