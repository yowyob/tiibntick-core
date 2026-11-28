package com.yowyob.tiibntick.core.administration.support;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * A fresh PostGIS database migrated by the real {@code tnt-administration-master.yaml}.
 *
 * <p>When {@code TNT_IT_DB_PORT} is set, an already running PostGIS is used ({@code TNT_IT_DB_HOST} localhost,
 * {@code TNT_IT_DB_USER} tiibntick, {@code TNT_IT_DB_PASSWORD} tiibntick_pass) and a throw-away database is
 * created on it; otherwise a Testcontainers {@code postgis/postgis:18-3.6} is started.
 */
public final class PostgisTestDatabase implements AutoCloseable {

    public static final String MASTER = "db/changelog/tnt-administration-master.yaml";

    private final PostgreSQLContainer<?> container;
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String database;
    private final String adminDatabase;

    private PostgisTestDatabase(PostgreSQLContainer<?> container, String host, int port, String user,
                                String password, String database, String adminDatabase) {
        this.container = container;
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.database = database;
        this.adminDatabase = adminDatabase;
    }

    @SuppressWarnings("resource")
    public static PostgisTestDatabase start() throws SQLException {
        String externalPort = System.getenv("TNT_IT_DB_PORT");
        if (externalPort != null && !externalPort.isBlank()) {
            String host = env("TNT_IT_DB_HOST", "localhost");
            String user = env("TNT_IT_DB_USER", "tiibntick");
            String password = env("TNT_IT_DB_PASSWORD", "tiibntick_pass");
            String admin = env("TNT_IT_DB_NAME", "tiibntick_core");
            String database = "tnt_l1_admin_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            int port = Integer.parseInt(externalPort.trim());
            try (Connection c = DriverManager.getConnection(jdbc(host, port, admin), user, password);
                 Statement st = c.createStatement()) {
                st.execute("CREATE DATABASE " + database);
            }
            return new PostgisTestDatabase(null, host, port, user, password, database, admin);
        }
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("tnt_admin_thresholds_it").withUsername("tnt_test").withPassword("tnt_test_secret");
        container.start();
        return new PostgisTestDatabase(container, container.getHost(),
                container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), container.getUsername(),
                container.getPassword(), container.getDatabaseName(), null);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String jdbc(String host, int port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public Connection jdbc() throws SQLException {
        return DriverManager.getConnection(jdbc(host, port, database), user, password);
    }

    /** Applies the module's Liquibase master. */
    public void migrate() throws Exception {
        try (Connection conn = jdbc()) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase(MASTER, new ClassLoaderResourceAccessor(), db).update(new Contexts(), new LabelExpression());
        }
    }

    /** Applies the module's Liquibase master except its last {@code n} changesets (to test an upgrade). */
    public void migrateAllBut(int n) throws Exception {
        try (Connection conn = jdbc()) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            Liquibase liquibase = new Liquibase(MASTER, new ClassLoaderResourceAccessor(), db);
            int pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression()).size();
            liquibase.update(pending - n, new Contexts(), new LabelExpression());
        }
    }

    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    public void execute(String sql) throws SQLException {
        try (Connection c = jdbc(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Override
    public void close() throws SQLException {
        if (container != null) {
            container.stop();
            return;
        }
        try (Connection c = DriverManager.getConnection(jdbc(host, port, adminDatabase), user, password);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }
}
