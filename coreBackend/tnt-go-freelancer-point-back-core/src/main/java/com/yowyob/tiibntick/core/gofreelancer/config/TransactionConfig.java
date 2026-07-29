package com.yowyob.tiibntick.core.gofreelancer.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

/**
 * Configuration class for transaction management.
 * Configures the reactive transaction manager for R2DBC.
 *
 * @author François-Charles ATANGA
 * @date 08/02/2026
 */
@Configuration
public class TransactionConfig {

    @Bean
    @Primary
    public ReactiveTransactionManager connectionFactoryTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
