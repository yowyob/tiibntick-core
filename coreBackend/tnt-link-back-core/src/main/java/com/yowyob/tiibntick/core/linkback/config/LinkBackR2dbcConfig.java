package com.yowyob.tiibntick.core.linkback.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Wires tnt-link-back-core's persistence to the platform's dedicated
 * {@code linkConnectionFactory} bean (TiiBnTick Database Pyramid, level 3 —
 * see tnt-bootstrap's {@code TntDataSourceConfig}) instead of the shared
 * {@code @Primary} {@code tntCoreConnectionFactory}, keeping the Link product's
 * {@code tnt_link} schema on its own connection pool as the platform moves
 * from monolith to per-platform databases.
 *
 * <p>{@code entityOperationsRef} scopes this module's repositories to
 * {@code linkR2dbcEntityTemplate} instead of the context-wide {@code @Primary}
 * template every other L2-L5 module implicitly uses — tnt-link-back-core is the
 * first consumer of a non-primary Database Pyramid connection factory.
 *
 * @author Dilane PAFE
 */
@Configuration
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository",
        entityOperationsRef = "linkR2dbcEntityTemplate")
public class LinkBackR2dbcConfig {

    @Bean("linkDatabaseClient")
    public DatabaseClient linkDatabaseClient(@Qualifier("linkConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean("linkR2dbcEntityTemplate")
    public R2dbcEntityTemplate linkR2dbcEntityTemplate(@Qualifier("linkConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }
}
