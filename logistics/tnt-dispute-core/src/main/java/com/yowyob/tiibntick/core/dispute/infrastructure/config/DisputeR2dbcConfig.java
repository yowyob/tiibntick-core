package com.yowyob.tiibntick.core.dispute.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * R2DBC configuration for tnt-dispute-core.
 *
 * <p>Enables reactive repositories in the persistence adapter package,
 * reactive auditing for {@code @CreatedDate} / {@code @LastModifiedDate},
 * and configures a reactive transaction manager for atomic dispute saves.
 *
 * @author MANFOUO Braun
 */
@Configuration
//@EnableR2dbcAuditing
@EnableTransactionManagement
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence"
})
public class DisputeR2dbcConfig {

    /**
     * Reactive transaction manager wrapping the R2DBC connection factory.
     * Used by {@code @Transactional} in application services.
     *
     * @param connectionFactory the auto-configured R2DBC connection factory
     * @return the reactive transaction manager
     */
    @Bean
    public ReactiveTransactionManager disputeTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
