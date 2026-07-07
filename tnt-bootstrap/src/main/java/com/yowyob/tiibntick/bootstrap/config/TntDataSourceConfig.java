package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.bootstrap.deployment.PlatformCode;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.beans.factory.annotation.Qualifier;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Spring configuration establishing the TiiBnTick Database Pyramid.
 * <p>
 * The pyramid has 3 levels, each with its own {@link ConnectionFactory}:
 * <ol>
 *   <li><b>Kernel DB</b> — shared with RT-comops (Yowyob Kernel modules)</li>
 *   <li><b>TNT Core DB</b> — primary, used by all tnt-****-core modules (same PostgreSQL instance
 *       in the monolith setup, different schemas)</li>
 *   <li><b>Platform DBs</b> — one factory per TiiBnTick platform (Agency, Go, Link, Point,
 *       Freelancer, Market). In the monolith setup these all point to the same PostgreSQL
 *       instance with platform-specific schemas.</li>
 * </ol>
 * <p>
 * In the current monolith-first development phase, all connection factories point to
 * the same PostgreSQL instance (different schemas). The architecture supports
 * per-platform databases when migrating to microservices.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class TntDataSourceConfig {

    // ── Common DB settings ───────────────────────────────────────────────────

    @Value("${DB_HOST:localhost}")            private String dbHost;
    @Value("${DB_PORT:5433}")                 private int dbPort;
    @Value("${DB_NAME:tiibntick_core}")       private String dbName;
    @Value("${DB_USER:tiibntick}")            private String dbUser;
    @Value("${DB_PASSWORD:tiibntick_pass}")   private String dbPassword;

    // TLS — the shared Yowyob PostgreSQL instance enforces it in staging/prod;
    // "disable" keeps local docker-compose (private network, no cert) working as-is.
    // Accepted values: disable, allow, prefer, require, verify-ca, verify-full, tunnel.
    @Value("${DB_SSL_MODE:disable}")          private String dbSslMode;

    // Pool sizing per ConnectionFactory. NOTE: 8 factories (kernel + core + 6 platforms)
    // share the same physical instance in monolith mode, so the effective connection
    // ceiling is up to (DB_POOL_MAX_SIZE × 8) — size DB_POOL_MAX_SIZE against the
    // max_connections quota the Yowyob DBA team grants this app, not against the whole server.
    @Value("${DB_POOL_INITIAL_SIZE:2}")       private int poolInitialSize;
    @Value("${DB_POOL_MAX_SIZE:10}")          private int poolMaxSize;
    @Value("${DB_POOL_MAX_IDLE_MINUTES:30}")  private long poolMaxIdleMinutes;

    // Kernel DB (may differ in production when RT-comops has its own DB)
    @Value("${KERNEL_DB_HOST:${DB_HOST:localhost}}") private String kernelDbHost;
    @Value("${KERNEL_DB_NAME:tiibntick_core}")       private String kernelDbName;

    // ── Level 1: Kernel DB ───────────────────────────────────────────────────

    @Bean("kernelConnectionFactory")
    public ConnectionFactory kernelDbConnectionFactory() {
        log.info("Configuring Kernel DB connection → {}:{}/{}", kernelDbHost, dbPort, kernelDbName);
        return buildFactory(kernelDbHost, dbPort, kernelDbName, dbUser, dbPassword);
    }

    // ── Level 2: TNT Core DB (Primary) ───────────────────────────────────────

    @Bean("tntCoreConnectionFactory")
    @Primary
    public ConnectionFactory tntCoreDbConnectionFactory() {
        log.info("Configuring TNT Core DB connection → {}:{}/{}", dbHost, dbPort, dbName);
        return buildFactory(dbHost, dbPort, dbName, dbUser, dbPassword);
    }
    
    // CRUCIAL ADDITION: Restore the default beans that were overridden by auto-configuration
    @Bean("databaseClient")
    @Primary
    public DatabaseClient databaseClient(@Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean("r2dbcEntityTemplate")
    @Primary
    public R2dbcEntityTemplate r2dbcEntityTemplate(@Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    // ── Level 3: Platform DBs ────────────────────────────────────────────────
    // In monolith mode: same PostgreSQL instance, platform schemas in same DB.
    // In microservices mode: one DB per platform (override via env vars).

    @Bean("agencyConnectionFactory")
    public ConnectionFactory tntAgencyDbConnectionFactory() {
        return platformFactory(PlatformCode.AGENCY);
    }

    @Bean("goConnectionFactory")
    public ConnectionFactory tntGoDbConnectionFactory() {
        return platformFactory(PlatformCode.GO);
    }

    @Bean("linkConnectionFactory")
    public ConnectionFactory tntLinkDbConnectionFactory() {
        return platformFactory(PlatformCode.LINK);
    }

    @Bean("pointConnectionFactory")
    public ConnectionFactory tntPointDbConnectionFactory() {
        return platformFactory(PlatformCode.POINT);
    }

    @Bean("freelancerConnectionFactory")
    public ConnectionFactory tntFreelancerDbConnectionFactory() {
        return platformFactory(PlatformCode.FREELANCER);
    }

    @Bean("marketConnectionFactory")
    public ConnectionFactory tntMarketDbConnectionFactory() {
        return platformFactory(PlatformCode.MARKET);
    }

    /**
     * Map of platform → connection factory for dynamic lookup in
     * {@link com.yowyob.tiibntick.bootstrap.health.TntDatabasePyramidHealthIndicator}.
     */
    @Bean
    public Map<PlatformCode, ConnectionFactory> platformConnectionFactories(
            ConnectionFactory agencyConnectionFactory,
            ConnectionFactory goConnectionFactory,
            ConnectionFactory linkConnectionFactory,
            ConnectionFactory pointConnectionFactory,
            ConnectionFactory freelancerConnectionFactory,
            ConnectionFactory marketConnectionFactory) {

        Map<PlatformCode, ConnectionFactory> map = new EnumMap<>(PlatformCode.class);
        map.put(PlatformCode.AGENCY, agencyConnectionFactory);
        map.put(PlatformCode.GO, goConnectionFactory);
        map.put(PlatformCode.LINK, linkConnectionFactory);
        map.put(PlatformCode.POINT, pointConnectionFactory);
        map.put(PlatformCode.FREELANCER, freelancerConnectionFactory);
        map.put(PlatformCode.MARKET, marketConnectionFactory);
        return map;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConnectionFactory platformFactory(PlatformCode platform) {
        // In monolith mode, all platforms share the same core DB
        // Platform-specific schema isolation is handled at the Liquibase / schema level
        String envHost = System.getenv(platform.name() + "_DB_HOST");
        String resolvedHost = envHost != null && !envHost.isBlank() ? envHost : dbHost;
        log.debug("Platform DB [{}] → {}:{}/{}", platform, resolvedHost, dbPort, dbName);
        return buildFactory(resolvedHost, dbPort, dbName, dbUser, dbPassword);
    }

    private ConnectionFactory buildFactory(String host, int port, String database,
                                           String user, String password) {
        PostgresqlConnectionFactory delegate = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(host)
                        .port(port)
                        .database(database)
                        .username(user)
                        .password(password)
                        .sslMode(SSLMode.fromValue(dbSslMode))
                        .build());

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(delegate)
                .initialSize(poolInitialSize)
                .maxSize(poolMaxSize)
                .maxIdleTime(Duration.ofMinutes(poolMaxIdleMinutes))
                .validationQuery("SELECT 1")
                .build();
        return new ConnectionPool(poolConfiguration);
    }
}
