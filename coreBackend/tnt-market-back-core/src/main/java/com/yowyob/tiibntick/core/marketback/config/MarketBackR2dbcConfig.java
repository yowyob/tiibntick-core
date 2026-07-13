package com.yowyob.tiibntick.core.marketback.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Wires tnt-market-back-core's persistence to the platform's dedicated
 * {@code marketConnectionFactory} bean (TiiBnTick Database Pyramid, level 3 —
 * see tnt-bootstrap's {@code TntDataSourceConfig}) instead of the shared
 * {@code @Primary} {@code tntCoreConnectionFactory}, keeping the Market
 * product's {@code tnt_market} schema on its own connection pool, following
 * the same pattern as {@code LinkBackR2dbcConfig} in tnt-link-back-core.
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository",
        entityOperationsRef = "marketR2dbcEntityTemplate")
public class MarketBackR2dbcConfig {

    @Bean("marketDatabaseClient")
    public DatabaseClient marketDatabaseClient(@Qualifier("marketConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean("marketR2dbcEntityTemplate")
    public R2dbcEntityTemplate marketR2dbcEntityTemplate(@Qualifier("marketConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
