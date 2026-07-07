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

    @Value("${spring.liquibase.url:jdbc:postgresql://localhost:5432/tiibntick_core}")
    private String jdbcUrl;

    @Value("${spring.liquibase.user:tiibntick}")
    private String jdbcUser;

    @Value("${spring.liquibase.password:tiibntick_pass}")
    private String jdbcPassword;

    @Value("${spring.liquibase.enabled:true}")
    private boolean liquibaseEnabled;

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

        log.info("Running TiiBnTick Core Liquibase migrations → {}", jdbcUrl);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(buildDataSource());
        liquibase.setChangeLog("classpath:db/changelog/tnt-core-master.yaml");
        liquibase.setShouldRun(true);
        return liquibase;
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
