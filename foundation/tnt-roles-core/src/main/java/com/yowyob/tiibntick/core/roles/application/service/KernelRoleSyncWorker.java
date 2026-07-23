package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains {@code tnt_role_sync_outbox} and replays each pending entry against the Kernel's
 * role/assignment HTTP endpoints (Chantier D · Audit n°6 · S5).
 *
 * <p>Same shape as {@code yow-event-kernel}'s {@code OutboxPollerService} — a distributed
 * lock ({@link SchedulerLock}) plus an in-process {@link AtomicBoolean} guard prevent
 * concurrent poll cycles, both locally and across a multi-instance deployment; the actual
 * row-claiming safety net is {@link RoleSyncOutboxRepository#fetchPendingBatch(int)}'s
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}.
 *
 * <p>Not registered as a Spring bean here — instantiated explicitly by a later wiring phase
 * (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class KernelRoleSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleSyncWorker.class);

    /** Once an entry has failed this many attempts, it is marked {@code DEAD} instead of retried. */
    static final int MAX_ATTEMPTS = 10;

    /** Backoff ceiling: {@code min(2^attemptCount seconds, 5 minutes)}. */
    static final long MAX_BACKOFF_SECONDS = 300L;

    private final RoleSyncOutboxRepository outboxRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final ITntRoleProvisioningPort provisioningPort;
    private final ITntRoleAssignmentPort assignmentPort;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    /** Guards against overlapping poll cycles within this instance. */
    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    public KernelRoleSyncWorker(RoleSyncOutboxRepository outboxRepository,
                                 RoleRepository roleRepository,
                                 UserRoleAssignmentRepository assignmentRepository,
                                 ITntRoleProvisioningPort provisioningPort,
                                 ITntRoleAssignmentPort assignmentPort,
                                 ObjectMapper objectMapper,
                                 int batchSize) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.provisioningPort = Objects.requireNonNull(provisioningPort);
        this.assignmentPort = Objects.requireNonNull(assignmentPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.batchSize = batchSize;
    }

    // ── Scheduled polling ────────────────────────────────────────────────────

    /**
     * {@code lockAtLeastFor} matters here for the same reason as
     * {@code OutboxPollerService}/{@code MediaFileCleanupScheduler}: {@link #poll()} only
     * subscribes the batch and returns immediately (fire-and-forget), so without a minimum
     * hold time ShedLock would release the lock before the subscribed batch actually
     * finishes, letting another instance pick up the same rows.
     */
    @Scheduled(fixedDelayString = "${tnt.roles.outbox.poll-interval-ms:2000}")
    @SchedulerLock(name = "tnt-roles-kernel-sync", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
    public void scheduledPoll() {
        LockAssert.assertLocked();
        poll().subscribe(
                count -> {
                    if (count > 0) {
                        log.debug("Role sync poll cycle completed: {} entries processed", count);
                    }
                },
                error -> log.error("Role sync poll cycle failed", error)
        );
    }

    /**
     * Fetches and processes one batch. Each entry is isolated via {@code onErrorResume} so a
     * single bad entry never stops the rest of the batch.
     *
     * @return the number of entries processed in this cycle
     */
    public Mono<Integer> poll() {
        if (!pollingInProgress.compareAndSet(false, true)) {
            log.trace("Role sync poll cycle skipped — previous cycle still running");
            return Mono.just(0);
        }
        return outboxRepository.fetchPendingBatch(batchSize)
                .flatMap(entry -> processEntry(entry).thenReturn(true))
                .count()
                .map(Long::intValue)
                .doFinally(signal -> pollingInProgress.set(false));
    }

    // ── Per-entry processing ─────────────────────────────────────────────────

    /**
     * Claims the entry ({@code asProcessing()}, persisted), dispatches it to the operation
     * handler, and on any failure computes the retry/dead transition. Never propagates an
     * error — the caller's batch must keep going regardless of what happens to one entry.
     */
    private Mono<Void> processEntry(RoleSyncOutboxEntry entry) {
        RoleSyncOutboxEntry processing = entry.asProcessing();
        return outboxRepository.save(processing)
                .flatMap(saved -> dispatch(saved).onErrorResume(error -> handleFailure(saved, error)))
                .onErrorResume(error -> {
                    log.error("Unexpected error processing role sync outbox entry {}: {}",
                            entry.id(), error.getMessage(), error);
                    return Mono.empty();
                });
    }

    private Mono<Void> dispatch(RoleSyncOutboxEntry entry) {
        return switch (entry.operation()) {
            case PROVISION_ROLE -> handleProvisionRole(entry);
            case DELETE_ROLE -> handleDeleteRole(entry);
            case ASSIGN_ROLE -> handleAssignRole(entry);
            case REVOKE_ASSIGNMENT -> handleRevokeAssignment(entry);
        };
    }

    /** Payload: {@code {"tenantId","code","name","scopeType","permissions":[...]}}. */
    private Mono<Void> handleProvisionRole(RoleSyncOutboxEntry entry) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(entry.payload());
        } catch (Exception e) {
            return handleFailure(entry, e);
        }
        UUID tenantId = UUID.fromString(payload.get("tenantId").asText());
        String code = payload.get("code").asText();
        String name = payload.get("name").asText();
        RoleScopeType scopeType = RoleScopeType.valueOf(payload.get("scopeType").asText());
        Set<String> permissions = toStringSet(payload.get("permissions"));

        TntRoleDefinition definition = new TntRoleDefinition(code, name, "", scopeType, permissions, false, true);

        return provisioningPort.provisionRole(tenantId, definition)
                .then(provisioningPort.findRoleId(tenantId, code))
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFoundInKernel(code)))
                .flatMap(kernelRoleId -> roleRepository.markKernelRoleId(tenantId, entry.aggregateId(), kernelRoleId)
                        .then(Mono.defer(() -> outboxRepository.save(entry.asProvisioned(kernelRoleId)))))
                .then();
    }

    /** Payload: {@code {"tenantId","kernelRoleId"}}. */
    private Mono<Void> handleDeleteRole(RoleSyncOutboxEntry entry) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(entry.payload());
        } catch (Exception e) {
            return handleFailure(entry, e);
        }
        UUID tenantId = UUID.fromString(payload.get("tenantId").asText());
        UUID kernelRoleId = UUID.fromString(payload.get("kernelRoleId").asText());

        return provisioningPort.deleteRole(tenantId, kernelRoleId)
                .then(Mono.defer(() -> outboxRepository.save(entry.asProvisioned(kernelRoleId))))
                .then();
    }

    /** Payload: {@code {"userId","roleCode","scopeType","scopeId"}}. */
    private Mono<Void> handleAssignRole(RoleSyncOutboxEntry entry) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(entry.payload());
        } catch (Exception e) {
            return handleFailure(entry, e);
        }
        UUID userId = UUID.fromString(payload.get("userId").asText());
        String roleCode = payload.get("roleCode").asText();
        String scopeType = payload.get("scopeType").asText();
        UUID scopeId = UUID.fromString(payload.get("scopeId").asText());

        return assignmentPort.assignRole(userId, roleCode, scopeType, scopeId)
                .flatMap(kernelAssignmentId -> assignmentRepository
                        .markKernelAssignmentId(entry.tenantId(), entry.aggregateId(), kernelAssignmentId)
                        .then(Mono.defer(() -> outboxRepository.save(entry.asProvisioned(kernelAssignmentId)))))
                .then();
    }

    /** Payload: {@code {"kernelAssignmentId"}}. */
    private Mono<Void> handleRevokeAssignment(RoleSyncOutboxEntry entry) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(entry.payload());
        } catch (Exception e) {
            return handleFailure(entry, e);
        }
        UUID kernelAssignmentId = UUID.fromString(payload.get("kernelAssignmentId").asText());

        return assignmentPort.revokeAssignment(kernelAssignmentId)
                .then(Mono.defer(() -> outboxRepository.save(entry.asProvisioned(kernelAssignmentId))))
                .then();
    }

    // ── Failure / backoff ────────────────────────────────────────────────────

    /**
     * Computes the retry-vs-dead transition and persists it.
     *
     * <p>{@code entry.attemptCount()} already reflects the attempt that just failed (bumped
     * by {@link RoleSyncOutboxEntry#asProcessing()} before this attempt was made) — once it
     * reaches {@link #MAX_ATTEMPTS}, the entry is marked {@link
     * com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus#DEAD DEAD} instead of
     * scheduled for another retry.
     */
    private Mono<Void> handleFailure(RoleSyncOutboxEntry entry, Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        log.warn("Kernel sync failed for outbox entry {} (operation={}, attempt={}): {}",
                entry.id(), entry.operation(), entry.attemptCount(), message);

        RoleSyncOutboxEntry updated;
        if (entry.attemptCount() >= MAX_ATTEMPTS) {
            updated = entry.asDead(message);
            log.error("Outbox entry {} exhausted {} attempts — marking DEAD: {}",
                    entry.id(), MAX_ATTEMPTS, message);
        } else {
            long backoffSeconds = Math.min(1L << entry.attemptCount(), MAX_BACKOFF_SECONDS);
            LocalDateTime nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds);
            updated = entry.asRetrying(message, nextAttemptAt);
        }
        return outboxRepository.save(updated).then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Set<String> toStringSet(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        arrayNode.forEach(node -> result.add(node.asText()));
        return result;
    }
}
