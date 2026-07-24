package com.yowyob.tiibntick.bootstrap.config;

import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Liquibase migration configuration for TiiBnTick Core.
 * <p>
 * Executes all module schema migrations in strict dependency order (L2 → L3 → L4 → L5).
 * Each module defines its own {@code db/changelog/db.changelog-master.yaml} which is
 * aggregated here through the master changelog at the bootstrap level.
 * <p>
 * Uses a blocking JDBC {@link DataSource} (NOT R2DBC) — Liquibase is inherently blocking
 * and runs only at startup before any reactive traffic is served.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class LiquibaseConfig {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    @Value("${spring.liquibase.url:jdbc:postgresql://localhost:5432/tiibntick_core}")
    private String jdbcUrl;

    @Value("${spring.liquibase.user:tiibntick}")
    private String jdbcUser;

    @Value("${spring.liquibase.password:tiibntick_pass}")
    private String jdbcPassword;

    @Value("${spring.liquibase.enabled:true}")
    private boolean liquibaseEnabled;

    // ── Auto-create the target database ─────────────────────────────────────
    // DB_USER is granted CREATEDB on the shared Yowyob PostgreSQL instance, so
    // the app can provision its own database instead of waiting on manual DBA
    // provisioning. CREATE DATABASE cannot run inside a transaction block and
    // can't target the database it's currently connected to, so this opens a
    // separate autocommit connection to a maintenance database first.
    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5433}")
    private int dbPort;

    @Value("${DB_NAME:tiibntick_core_prod}")
    private String dbName;

    @Value("${DB_SSL_MODE:disable}")
    private String dbSslMode;

    @Value("${DB_MAINTENANCE_DATABASE:postgres}")
    private String maintenanceDatabase;

    @Value("${DB_AUTO_CREATE_DATABASE:true}")
    private boolean autoCreateDatabase;

    /**
     * Primary Liquibase bean — runs the master changelog that includes all module migrations.
     * The master changelog is at {@code classpath:db/changelog/tnt-core-master.yaml}.
     */
    @Bean
    public SpringLiquibase springLiquibase() {
        if (!liquibaseEnabled) {
            log.info("Liquibase is disabled — skipping schema migrations");
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setShouldRun(false);
            return liquibase;
        }

        if (autoCreateDatabase) {
            ensureDatabaseExists();
        }

        log.info("Running TiiBnTick Core Liquibase migrations → {}", jdbcUrl);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(buildDataSource());
        liquibase.setChangeLog("classpath:db/changelog/tnt-core-master.yaml");
        liquibase.setShouldRun(true);
        return liquibase;
    }

    /**
     * Creates {@link #dbName} on {@link #dbHost}:{@link #dbPort} if it doesn't exist yet.
     * Idempotent — safe to run against an already-provisioned database (no-ops).
     */
    private void ensureDatabaseExists() {
        if (!SAFE_IDENTIFIER.matcher(dbName).matches()) {
            throw new IllegalStateException(
                    "DB_NAME '" + dbName + "' is not a safe SQL identifier — refusing to auto-create it");
        }

        String maintenanceUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                dbHost, dbPort, maintenanceDatabase, dbSslMode);

        try (Connection connection = DriverManager.getConnection(maintenanceUrl, jdbcUser, jdbcPassword)) {
            connection.setAutoCommit(true);

            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
                check.setString(1, dbName);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        log.debug("Database '{}' already exists — nothing to create", dbName);
                        return;
                    }
                }
            }

            log.warn("Database '{}' does not exist on {}:{} — creating it now (DB_AUTO_CREATE_DATABASE=true)",
                    dbName, dbHost, dbPort);
            try (Statement create = connection.createStatement()) {
                create.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
            }
            log.info("Database '{}' created successfully", dbName);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to auto-create database '" + dbName + "' via maintenance database '"
                            + maintenanceDatabase + "' on " + dbHost + ":" + dbPort
                            + " — DB_USER needs CREATEDB privilege there. Set DB_AUTO_CREATE_DATABASE=false "
                            + "to disable this and fall back to manual DBA provisioning instead.", e);
        }
    }

    private DataSource buildDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(jdbcUrl);
        ds.setUsername(jdbcUser);
        ds.setPassword(jdbcPassword);
        return ds;
    }
}
