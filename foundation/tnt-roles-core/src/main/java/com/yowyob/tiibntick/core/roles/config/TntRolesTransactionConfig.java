package com.yowyob.tiibntick.core.roles.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Standalone {@link ReactiveTransactionManager}/{@link TransactionalOperator} wiring for
 * {@code tnt-roles-core}'s RBAC application services (Chantier D · Audit n°6 · S5).
 *
 * <p>Local RBAC writes ({@code Role}/{@code UserRoleAssignment}) and their corresponding
 * {@code RoleSyncOutboxEntry} row must commit together in the same local transaction —
 * otherwise a crash between the two writes could either lose the local change or leave it
 * un-synced to the Kernel forever. No {@code ReactiveTransactionManager} precedent existed
 * anywhere in this repo's R2DBC modules before this — this is the first.
 *
 * <p>Uses the shared {@code tntCoreConnectionFactory} bean (the module's own R2DBC
 * repositories already resolve against it via {@code tnt-bootstrap}'s
 * {@code TntDataSourceConfig}), so transactions here span exactly the same connection pool
 * {@link com.yowyob.tiibntick.core.roles.adapter.out.persistence.RoleRepositoryAdapter},
 * {@link com.yowyob.tiibntick.core.roles.adapter.out.persistence.UserRoleAssignmentRepositoryAdapter},
 * and {@link com.yowyob.tiibntick.core.roles.adapter.out.persistence.RoleSyncOutboxRepositoryAdapter}
 * already use.
 *
 * <p><b>Deliberately not wired into {@link TntRolesAutoConfiguration}</b> — no {@code @Import}
 * or bean registration references this class anywhere yet. A later integration pass wires it
 * in and injects {@link TransactionalOperator} into {@code TntRoleAssignmentService},
 * {@code TntRoleRevocationService}, and {@code TntRoleManagementService}.
 *
 * @author MANFOUO Braun
 */
@Configuration
public class TntRolesTransactionConfig {

    @Bean
    public ReactiveTransactionManager tntRolesTransactionManager(
            @Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    public TransactionalOperator tntRolesTransactionalOperator(ReactiveTransactionManager tntRolesTransactionManager) {
        return TransactionalOperator.create(tntRolesTransactionManager);
    }
}
