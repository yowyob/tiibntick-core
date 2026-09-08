package com.yowyob.tiibntick.bootstrap.health;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Reactive health indicator verifying connectivity to the TiiBnTick database pyramid.
 * Exposed at {@code /actuator/health/database-pyramid}.
 * <p>
 * In the dev monolith setup, all schemas live in a single PostgreSQL instance.
 * The check validates the core R2DBC connection factory using a lightweight
 * {@code SELECT 1} probe.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component("database-pyramid")
@RequiredArgsConstructor
public class TntDatabasePyramidHealthIndicator implements ReactiveHealthIndicator {

    private final ConnectionFactory connectionFactory;

    @Override
    public Mono<Health> health() {
        return checkCoreDb()
                .map(coreOk -> {
                    DatabasePyramidStatus status = DatabasePyramidStatus.builder()
                            .kernelDbConnected(coreOk)
                            .tntCoreDbConnected(coreOk)
                            .agencyDbConnected(coreOk)
                            .goDbConnected(coreOk)
                            .linkDbConnected(coreOk)
                            .pointDbConnected(coreOk)
                            .freelancerDbConnected(coreOk)
                            .marketDbConnected(coreOk)
                            .checkedAt(LocalDateTime.now())
                            .build();

                    return buildHealth(status);
                })
                .onErrorResume(ex -> {
                    log.error("Database pyramid health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .build());
                });
    }

    private Mono<Boolean> checkCoreDb() {
        return Mono.usingWhen(
                        connectionFactory.create(),
                        conn -> Mono.from(conn.createStatement("SELECT 1").execute())
                                .flatMap(result -> Mono.from(result.getRowsUpdated())),
                        conn -> Mono.from(conn.close()),
                        (conn, ex) -> Mono.from(conn.close()),
                        conn -> Mono.from(conn.close())
                )
                .map(rows -> true)
                .onErrorReturn(false);
    }

    private Health buildHealth(DatabasePyramidStatus status) {
        Health.Builder builder = status.allConnected() ? Health.up() : Health.down();
        builder.withDetail("connected", status.connectedDatabases())
               .withDetail("failed", status.failedDatabases())
               .withDetail("checkedAt", status.getCheckedAt().toString());
        return builder.build();
    }
}
