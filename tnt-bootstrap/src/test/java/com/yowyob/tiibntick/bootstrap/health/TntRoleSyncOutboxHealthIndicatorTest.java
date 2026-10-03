package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.contributor.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot C-20.2 — a DEAD Kernel role-sync entry is visible both as a gauge and as a degraded
 * health status. The DEAD entry is written through the outbox port's {@code save} by the
 * test itself, the same call {@code KernelRoleSyncWorker} makes when retries run out.
 */
class TntRoleSyncOutboxHealthIndicatorTest {

    private static final UUID SYSTEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private InMemoryOutbox outbox;
    private SimpleMeterRegistry registry;
    private TntRoleSyncOutboxHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        outbox = new InMemoryOutbox();
        registry = new SimpleMeterRegistry();
        indicator = new TntRoleSyncOutboxHealthIndicator(outbox, registry);
    }

    @Test
    void emptyOutbox_up_gaugesAtZero() {
        StepVerifier.create(indicator.health())
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.UP);
                    assertThat(h.getDetails()).containsEntry("dead", 0);
                })
                .verifyComplete();

        assertThat(gauge("DEAD")).isZero();
        assertThat(gauge("RETRYING")).isZero();
    }

    @Test
    void deadEntry_degradesHealth_andGaugeReadsOne() {
        RoleSyncOutboxEntry dead = RoleSyncOutboxEntry.pending(RoleSyncOperation.PROVISION_ROLE,
                        RoleSyncAggregateType.ROLE, UUID.randomUUID(), SYSTEM_TENANT, "{\"code\":\"FREELANCER\"}")
                .asProcessing()
                .asDead("Failed to provision TiiBnTick role 'FREELANCER': 403 Forbidden from POST /api/roles");
        outbox.save(dead).block();
        outbox.save(RoleSyncOutboxEntry.pending(RoleSyncOperation.ASSIGN_ROLE,
                        RoleSyncAggregateType.ASSIGNMENT, UUID.randomUUID(), SYSTEM_TENANT, "{}")
                .asProcessing()
                .asRetrying("Role 'FREELANCER' is not provisioned", LocalDateTime.now().plusMinutes(1))).block();

        StepVerifier.create(indicator.health())
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(TntRoleSyncOutboxHealthIndicator.DEGRADED);
                    assertThat(h.getDetails())
                            .containsEntry("dead", 1)
                            .containsEntry("retrying", 1)
                            .containsEntry("dead_by_operation", Map.of("PROVISION_ROLE", 1L))
                            .containsEntry("oldest_dead_last_error", dead.lastError());
                })
                .verifyComplete();

        // A level, readable at any time — not an event counter.
        assertThat(gauge("DEAD")).isEqualTo(1.0);
        assertThat(gauge("RETRYING")).isEqualTo(1.0);
        assertThat(gauge("PENDING")).isZero();
    }

    @Test
    void scheduledRefresh_updatesGaugeWithoutAnyHealthCall() {
        assertThat(gauge("DEAD")).isEqualTo(-1.0); // not measured yet
        outbox.save(RoleSyncOutboxEntry.pending(RoleSyncOperation.ASSIGN_ROLE,
                RoleSyncAggregateType.ASSIGNMENT, UUID.randomUUID(), SYSTEM_TENANT, "{}").asDead("x")).block();

        indicator.refreshGauges();

        assertThat(gauge("DEAD")).isEqualTo(1.0);
    }

    @Test
    void degraded_winsOverUp_withTheConfiguredOrder() {
        // Same order as management.endpoint.health.status.order in application.yml.
        SimpleStatusAggregator aggregator =
                new SimpleStatusAggregator("DOWN", "OUT_OF_SERVICE", "DEGRADED", "UP", "UNKNOWN");

        assertThat(aggregator.getAggregateStatus(Set.of(Status.UP, TntRoleSyncOutboxHealthIndicator.DEGRADED)))
                .isEqualTo(TntRoleSyncOutboxHealthIndicator.DEGRADED);
        assertThat(aggregator.getAggregateStatus(Set.of(Status.DOWN, TntRoleSyncOutboxHealthIndicator.DEGRADED)))
                .isEqualTo(Status.DOWN);
    }

    private double gauge(String status) {
        return registry.get(TntRoleSyncOutboxHealthIndicator.GAUGE_NAME).tag("status", status).gauge().value();
    }

    /** Minimal in-memory implementation of the outbox port. */
    private static final class InMemoryOutbox implements RoleSyncOutboxRepository {
        private final Map<UUID, RoleSyncOutboxEntry> rows = new ConcurrentHashMap<>();

        @Override
        public Mono<RoleSyncOutboxEntry> save(RoleSyncOutboxEntry entry) {
            rows.put(entry.id(), entry);
            return Mono.just(entry);
        }

        @Override
        public Flux<RoleSyncOutboxEntry> fetchPendingBatch(int batchSize) {
            return Flux.fromIterable(rows.values())
                    .filter(e -> e.status() == RoleSyncStatus.PENDING || e.status() == RoleSyncStatus.RETRYING)
                    .take(batchSize);
        }

        @Override
        public Flux<RoleSyncOutboxEntry> findByAggregateId(UUID aggregateId) {
            return Flux.fromIterable(rows.values()).filter(e -> e.aggregateId().equals(aggregateId));
        }

        @Override
        public Flux<RoleSyncOutboxEntry> findByStatus(RoleSyncStatus status) {
            return Flux.fromIterable(rows.values()).filter(e -> e.status() == status);
        }
    }
}
