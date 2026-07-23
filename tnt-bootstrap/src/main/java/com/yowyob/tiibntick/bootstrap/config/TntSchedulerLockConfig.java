package com.yowyob.tiibntick.bootstrap.config;

import io.r2dbc.spi.ConnectionFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single ShedLock configuration — the ONLY place the {@link LockProvider} bean is defined
 * (Chantier D · Audit n°6 · S2).
 *
 * <p>Before this, none of the ~10 {@code @Scheduled} jobs spread across as many modules
 * (dispute SLA sweep, incident auto-escalation/SLA monitor, wallet reconciliation, overdue
 * invoice check, media cleanup, sync/realtime maintenance sweeps, the agency hub parcel
 * expiry job, trust's health poller and retry drainer, the event-kernel outbox poller) had
 * any distributed lock — every application instance in a multi-instance deployment ran
 * every one of them independently and concurrently, from duplicate SLA escalations to
 * concurrent wallet reconciliation runs racing each other.
 *
 * <p>Each owning module depends only on {@code shedlock-spring} (for the
 * {@code @SchedulerLock} annotation used directly on its {@code @Scheduled} methods) —
 * this is the one place {@code shedlock-provider-r2dbc} is wired, against the same
 * {@code tntCoreConnectionFactory} every module's own R2DBC repositories already share
 * (see {@link TntDataSourceConfig}), backed by the {@code shedlock} table created in
 * {@code db/changelog/changes/000-create-shedlock-table.sql}.
 *
 * <p>R2DBC support in ShedLock is explicitly marked "experimental" upstream, but that
 * disclaimer is about genuinely reactive (Mono/Flux-returning) locking, which isn't what
 * is needed here: {@code @SchedulerLock} wraps a classic {@code @Scheduled void} method
 * invocation on Spring's {@code TaskScheduler} worker thread — never a Netty event-loop
 * thread — so the brief blocking lock acquisition/release ShedLock performs internally is
 * exactly the kind of blocking call that is safe outside the reactive request pipeline
 * (contrast with {@code TenantValidationAspect}/{@code DisputeEventConsumer}'s {@code .block()}
 * calls fixed under Chantier D · Audit n°1 A3 / Audit n°6 S14, which block on request/consumer
 * threads instead).
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class TntSchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(@Qualifier("tntCoreConnectionFactory") ConnectionFactory connectionFactory) {
        return new R2dbcLockProvider(connectionFactory);
    }
}
