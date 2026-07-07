package com.yowyob.tiibntick.core.sync.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class SyncR2dbcConfig {

    @Bean("syncEntityTemplate")
    public R2dbcEntityTemplate syncEntityTemplate(@Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    @Bean("syncDatabaseClient")
    public DatabaseClient syncDatabaseClient(@Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }
}
