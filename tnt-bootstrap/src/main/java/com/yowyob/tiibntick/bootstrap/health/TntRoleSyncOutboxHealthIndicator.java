package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes dead Kernel role-sync entries impossible to ignore (lot C-20.2).
 *
 * <p>{@code KernelRoleSyncWorker} logs one {@code ERROR} when an outbox entry exhausts its
 * retries and turns {@code DEAD}, then nothing ever again: after the next restart the
 * {@code tnt_role_sync_outbox} table is the only trace, and nobody reads it. Fourteen
 * entries sat there unnoticed — ten {@code PROVISION_ROLE} since 2026-08-05 (Kernel
 * {@code 403} on {@code POST /api/roles}) and every {@code ASSIGN_ROLE} of FREELANCER since.
 *
 * <ul>
 *   <li><strong>Gauge</strong> {@code tnt.roles.sync.outbox.entries{status=DEAD|RETRYING|PENDING}}:
 *       the number of rows currently in that status — a level readable at any time (Prometheus
 *       {@code tnt_roles_sync_outbox_entries}), not an event counter. Refreshed every
 *       {@code tnt.roles.sync.outbox.monitor-interval-ms} (default 60 s) and on every health
 *       check.</li>
 *   <li><strong>Health</strong> {@code roleSyncOutbox}: {@link #DEGRADED} as soon as one
 *       {@code DEAD} row exists — a dead entry means a side effect the application announced
 *       (a role or an assignment pushed to the Kernel) never happened. {@code DEGRADED} is
 *       ordered between {@code OUT_OF_SERVICE} and {@code UP} and mapped to HTTP 200
 *       ({@code management.endpoint.health.status.*}): the aggregate {@code /actuator/health}
 *       turns {@code DEGRADED}, but no orchestrator restarts the pod for a Kernel-side refusal
 *       a restart cannot fix. Liveness/readiness groups do not include it.</li>
 * </ul>
 *
 * <p>Read-only: never purges, retries or rewrites an entry — dead rows are the evidence.
 *
 * @author KOUAM Kamdem
 */
@Slf4j
@Component("roleSyncOutbox")
public class TntRoleSyncOutboxHealthIndicator implements ReactiveHealthIndicator {

    /** Health status for "running, but a promised side effect did not happen". */
    public static final Status DEGRADED = new Status("DEGRADED",
            "Kernel role-sync outbox holds DEAD entries — see details.dead_by_operation");

    static final String GAUGE_NAME = "tnt.roles.sync.outbox.entries";
    static final List<RoleSyncStatus> GAUGED = List.of(
            RoleSyncStatus.DEAD, RoleSyncStatus.RETRYING, RoleSyncStatus.PENDING);

    private final RoleSyncOutboxRepository outboxRepository;
    private final Map<RoleSyncStatus, AtomicLong> levels = new EnumMap<>(RoleSyncStatus.class);

    public TntRoleSyncOutboxHealthIndicator(RoleSyncOutboxRepository outboxRepository,
                                            MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        for (RoleSyncStatus status : GAUGED) {
            AtomicLong level = new AtomicLong(-1); // -1 = not measured yet
            levels.put(status, level);
            Gauge.builder(GAUGE_NAME, level, AtomicLong::get)
                    .description("Kernel role-sync outbox rows currently in this status")
                    .tag("status", status.name())
                    .register(meterRegistry);
        }
    }

    /** Keeps the gauges current even when nobody calls /actuator/health. */
    @Scheduled(fixedDelayString = "${tnt.roles.sync.outbox.monitor-interval-ms:60000}",
            initialDelayString = "${tnt.roles.sync.outbox.monitor-initial-delay-ms:15000}")
    public void refreshGauges() {
        snapshot()
                .doOnError(e -> log.warn("Role-sync outbox monitor: refresh failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    @Override
    public Mono<Health> health() {
        return snapshot().map(this::toHealth);
    }

    Mono<Snapshot> snapshot() {
        return Flux.fromIterable(GAUGED)
                .concatMap(status -> outboxRepository.findByStatus(status).collectList()
                        .map(rows -> Map.entry(status, rows)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(byStatus -> {
                    byStatus.forEach((status, rows) -> levels.get(status).set(rows.size()));
                    return new Snapshot(byStatus);
                });
    }

    private Health toHealth(Snapshot s) {
        List<RoleSyncOutboxEntry> dead = s.rows(RoleSyncStatus.DEAD);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dead", dead.size());
        details.put("retrying", s.rows(RoleSyncStatus.RETRYING).size());
        details.put("pending", s.rows(RoleSyncStatus.PENDING).size());
        if (dead.isEmpty()) {
            return Health.up().withDetails(details).build();
        }
        Map<String, Long> deadByOperation = new LinkedHashMap<>();
        dead.forEach(e -> deadByOperation.merge(e.operation().name(), 1L, Long::sum));
        details.put("dead_by_operation", deadByOperation);
        oldest(dead).ifPresent(e -> {
            details.put("oldest_dead_created_at", String.valueOf(e.createdAt()));
            details.put("oldest_dead_last_error", e.lastError());
        });
        newest(dead).ifPresent(e -> details.put("newest_dead_created_at", String.valueOf(e.createdAt())));
        return Health.status(DEGRADED).withDetails(details).build();
    }

    private static Optional<RoleSyncOutboxEntry> oldest(List<RoleSyncOutboxEntry> rows) {
        return rows.stream().filter(e -> e.createdAt() != null)
                .min(Comparator.comparing(RoleSyncOutboxEntry::createdAt));
    }

    private static Optional<RoleSyncOutboxEntry> newest(List<RoleSyncOutboxEntry> rows) {
        return rows.stream().filter(e -> e.createdAt() != null)
                .max(Comparator.comparing(RoleSyncOutboxEntry::createdAt));
    }

    record Snapshot(Map<RoleSyncStatus, List<RoleSyncOutboxEntry>> byStatus) {
        List<RoleSyncOutboxEntry> rows(RoleSyncStatus status) {
            return byStatus.getOrDefault(status, List.of());
        }
    }

    /** Visible for tests: last measured level for a status, -1 before the first refresh. */
    long level(RoleSyncStatus status) {
        return levels.get(status).get();
    }
}
