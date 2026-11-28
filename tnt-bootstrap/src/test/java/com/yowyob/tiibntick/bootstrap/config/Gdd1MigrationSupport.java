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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * Shared plumbing of the lot L1 migration tests ({@code gdd-l1}): a fresh database per test on a PostGIS
 * server, the production master changelog, and a generic row seeder for the legacy tables.
 *
 * <p>The server is the one named by {@code tnt.it.pg.url} (with {@code tnt.it.pg.user} /
 * {@code tnt.it.pg.password}, or the {@code TNT_IT_PG_*} environment variables) when given, otherwise a
 * Testcontainers {@code postgis/postgis:18-3.6} started once for the JVM.
 */
final class Gdd1MigrationSupport {

    static final String MASTER = "db/changelog/tnt-core-master.yaml";
    static final String LOT_LABEL = "gdd-l1";

    private static PostgreSQLContainer<?> container;

    private Gdd1MigrationSupport() {
    }

    private static String setting(String property, String env) {
        String v = System.getProperty(property);
        return v != null && !v.isBlank() ? v : System.getenv(env);
    }

    private static synchronized String[] server() {
        String url = setting("tnt.it.pg.url", "TNT_IT_PG_URL");
        if (url != null && !url.isBlank()) {
            return new String[] {url, setting("tnt.it.pg.user", "TNT_IT_PG_USER"),
                    setting("tnt.it.pg.password", "TNT_IT_PG_PASSWORD")};
        }
        if (container == null) {
            @SuppressWarnings("resource")
            PostgreSQLContainer<?> c = new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("postgres").withUsername("tnt_test").withPassword("tnt_test_secret");
            c.start();
            container = c;
        }
        return new String[] {container.getJdbcUrl(), container.getUsername(), container.getPassword()};
    }

    /** A connection to a brand new, empty database (auto-commit on). */
    static Connection freshDatabase() throws Exception {
        String[] s = server();
        String name = "tnt_gdd1_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(s[0], s[1], s[2]); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + name);
        }
        String url = s[0].replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Connection conn = DriverManager.getConnection(url, s[1], s[2]);
        conn.setAutoCommit(true);
        return conn;
    }

    static Liquibase liquibase(Connection conn) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        ResourceAccessor accessor = com.yowyob.tiibntick.bootstrap.config.BootstrapChangelogs.accessor();
        return new Liquibase(MASTER, accessor, database);
    }

    /** The schema as the previous version left it: every changeset except the lot's. */
    static void migrateAsPreviousVersion(Connection conn) throws Exception {
        liquibase(conn).update(new Contexts(), new LabelExpression("!" + LOT_LABEL + " and !gdd-final"));
        conn.setAutoCommit(true);
    }

    /** The application restarts on the new version: validate, then apply what is left. */
    static Liquibase upgrade(Connection conn) throws Exception {
        Liquibase upgrade = liquibase(conn);
        upgrade.validate();
        upgrade.update(new Contexts(), new LabelExpression("!gdd-final"));
        conn.setAutoCommit(true);
        return upgrade;
    }

    static List<String> unrun(Liquibase liquibase) throws Exception {
        return liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression("!gdd-final")).stream()
                .map(cs -> cs.getId()).toList();
    }

    static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static Object value(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getObject(1) : null;
        }
    }

    static void exec(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    /** Runs one of the lot's SQL files again, as a re-run would (idempotence check). */
    static void rerun(Connection conn, String classpathSql) throws Exception {
        try (InputStream in = Gdd1MigrationSupport.class.getClassLoader().getResourceAsStream(classpathSql)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + classpathSql);
            }
            exec(conn, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Legacy data seeding: foreign keys are not enforced while it runs (the legacy tables reference a web of
     * persons, clients and addresses that the migrations under test do not read).
     */
    static void seeding(Connection conn, SqlBlock block) throws Exception {
        exec(conn, "SET session_replication_role = replica");
        try {
            block.run();
        } finally {
            exec(conn, "SET session_replication_role = origin");
        }
    }

    interface SqlBlock {
        void run() throws Exception;
    }

    /**
     * Inserts one row: the given values (SQL literals), plus a neutral value for every other NOT NULL
     * column without a default.
     */
    static void insert(Connection conn, String table, Map<String, String> values) throws Exception {
        Map<String, String> row = new LinkedHashMap<>(values);
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = '" + table + "' AND is_nullable = 'NO' AND column_default IS NULL")) {
            while (rs.next()) {
                String col = rs.getString(1);
                if (row.containsKey(col)) {
                    continue;
                }
                row.put(col, switch (rs.getString(2)) {
                    case "uuid" -> "'" + UUID.randomUUID() + "'";
                    case "boolean" -> "false";
                    case "integer", "bigint", "smallint", "numeric", "double precision", "real" -> "1";
                    case "timestamp without time zone", "timestamp with time zone", "date" -> "now()";
                    case "character varying", "text", "character" -> "'" + col.substring(0, Math.min(col.length(), 3)).toUpperCase() + UUID.randomUUID().toString().substring(0, 4) + "'";
                    default -> "'x'";
                });
            }
        }
        List<String> cols = new ArrayList<>(row.keySet());
        List<String> vals = cols.stream().map(row::get).toList();
        exec(conn, "INSERT INTO " + table + " (" + String.join(",", cols) + ") VALUES (" + String.join(",", vals) + ")");
    }

    static String q(Object v) {
        return v == null ? "NULL" : "'" + v + "'";
    }
}
