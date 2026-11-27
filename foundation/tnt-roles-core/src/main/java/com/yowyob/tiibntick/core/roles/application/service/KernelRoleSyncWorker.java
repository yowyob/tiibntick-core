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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * How long polling pauses after the Kernel circuit breaker refuses a call. Matches
     * {@code resilience4j.circuitbreaker.instances.kernel*WebClient.wait-duration-in-open-state}
     * (20s), so the first cycle after the pause lands on a breaker that has had its chance
     * to move to HALF_OPEN.
     */
    static final long CIRCUIT_PAUSE_SECONDS = 20L;

    /**
     * Rows claimed per cycle while the Kernel circuit is still recovering. A HALF_OPEN
     * breaker permits only a handful of trial calls, so claiming a full batch would hand it
     * dozens of calls to refuse — exactly the amplification this worker must not cause.
     */
    static final int PROBE_BATCH_SIZE = 1;

    private final RoleSyncOutboxRepository outboxRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final ITntRoleProvisioningPort provisioningPort;
    private final ITntRoleAssignmentPort assignmentPort;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    /**
     * How long a claim may stay unfinished before {@link #scheduledStaleClaimSweep()} hands the
     * row back to the queue. Must stay well above the longest legitimate attempt — the Kernel
     * call timeout and this poller's {@code lockAtMostFor} — or a row still being processed
     * elsewhere would be given to a second instance.
     */
    private final Duration staleClaimTimeout;

    /** Guards against overlapping poll cycles within this instance. */
    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    /**
     * Polling is suspended until this instant; {@code null} means not suspended.
     * Package-private, like {@link #MAX_ATTEMPTS}, so the unit test can drive the recovery
     * path without waiting {@link #CIRCUIT_PAUSE_SECONDS} of wall-clock time.
     */
    final AtomicReference<LocalDateTime> pausedUntil = new AtomicReference<>();

    /** While true, cycles claim {@link #PROBE_BATCH_SIZE} rows instead of a full batch. */
    final AtomicBoolean probing = new AtomicBoolean(false);

    /** Set by {@link #handleFailure} when the circuit refused a call in the current cycle. */
    private final AtomicBoolean circuitRefusedThisCycle = new AtomicBoolean(false);

    public KernelRoleSyncWorker(RoleSyncOutboxRepository outboxRepository,
                                 RoleRepository roleRepository,
                                 UserRoleAssignmentRepository assignmentRepository,
                                 ITntRoleProvisioningPort provisioningPort,
                                 ITntRoleAssignmentPort assignmentPort,
                                 ObjectMapper objectMapper,
                                 int batchSize,
                                 Duration staleClaimTimeout) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.provisioningPort = Objects.requireNonNull(provisioningPort);
        this.assignmentPort = Objects.requireNonNull(assignmentPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.batchSize = batchSize;
        this.staleClaimTimeout = Objects.requireNonNull(staleClaimTimeout);
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
        LocalDateTime resumeAt = pausedUntil.get();
        if (resumeAt != null && LocalDateTime.now().isBefore(resumeAt)) {
            pollingInProgress.set(false);
            log.debug("Role sync poll cycle skipped — Kernel circuit refused calls, polling paused until {}",
                    resumeAt);
            return Mono.just(0);
        }
        int effectiveBatchSize = probing.get() ? PROBE_BATCH_SIZE : batchSize;
        circuitRefusedThisCycle.set(false);
        return outboxRepository.fetchPendingBatch(effectiveBatchSize)
                .flatMap(entry -> processEntry(entry).thenReturn(true))
                .count()
                .map(Long::intValue)
                .doOnNext(count -> {
                    if (count > 0 && !circuitRefusedThisCycle.get()) {
                        // The probe got through: the circuit is closed again, resume full batches.
                        if (probing.compareAndSet(true, false)) {
                            log.info("Kernel circuit is serving calls again — role sync resumes full batches of {}",
                                    batchSize);
                        }
                        pausedUntil.set(null);
                    }
                })
                .doFinally(signal -> pollingInProgress.set(false));
    }

    // ── Lost claims ──────────────────────────────────────────────────────────

    /**
     * Puts back into the queue the entries claimed and never finished.
     *
     * <p>{@link #processEntry} marks a row PROCESSING before dispatching it. If this process
     * stops in between — a restart, an OOM kill, a lost instance — the row stays PROCESSING,
     * and {@link RoleSyncOutboxRepository#fetchPendingBatch(int)} only ever returns PENDING and
     * RETRYING rows. Such a row therefore has <em>no</em> recovery path: it is never retried,
     * never declared dead, and no count reports it as late. 274 of them were counted on
     * 27 November 2026, the oldest predating the Kernel outage — roles and assignments the
     * platform had accepted to synchronise and silently dropped.
     *
     * <p>Separate from {@link #poll()} on purpose: polling runs every two seconds and this
     * sweep has nothing to do that often. It also runs when polling is paused by the circuit
     * breaker — a lost claim is lost whatever the Kernel is doing, and putting it back in the
     * queue costs no Kernel call.
     */
    @Scheduled(fixedDelayString = "${tnt.roles.outbox.stale-claim-sweep-ms:60000}",
            initialDelayString = "${tnt.roles.outbox.stale-claim-sweep-initial-delay-ms:30000}")
    @SchedulerLock(name = "tnt-roles-stale-claim-sweep", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void scheduledStaleClaimSweep() {
        LockAssert.assertLocked();
        recoverStaleClaims().subscribe(
                count -> { },
                error -> log.error("Stale-claim sweep failed", error)
        );
    }

    /**
     * Requeues the lost claims and says how many — a non-zero count is always worth a line in
     * the log, since it means an earlier attempt disappeared without trace.
     *
     * @return the number of entries put back into the queue
     */
    public Mono<Long> recoverStaleClaims() {
        return outboxRepository.requeueStaleClaims(staleClaimTimeout)
                .doOnNext(count -> {
                    if (count > 0) {
                        log.warn("Requeued {} role sync outbox {} claimed more than {} ago and never completed "
                                        + "— an attempt was interrupted without recording its outcome",
                                count, count == 1 ? "entry" : "entries", staleClaimTimeout);
                    }
                });
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
            // UPDATE_ROLE is local-only: the Kernel exposes no PUT/PATCH for roles.
            // Mark PROVISIONED immediately so the entry leaves PROCESSING and doesn't
            // accumulate as a zombie (processedAt stamped, kernelRefId stays null).
            case UPDATE_ROLE -> outboxRepository.save(entry.asProvisioned(entry.kernelRefId())).then();
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

        // Chemin SOLUTION, et non celui de l'appelant : ici il n'y a pas d'appelant. Le worker
        // tourne en arrière-plan, le contexte réactif est vide, aucun jeton n'est propagé — d'où
        // les 403 et les sept entrées mortes depuis le 3 octobre 2026.
        return assignmentPort.assignRoleAsSolution(userId, roleCode, scopeType, scopeId)
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
     *
     * <p><strong>A circuit-breaker rejection is not a failed attempt.</strong> When the
     * Kernel circuit is OPEN or HALF_OPEN the call never leaves this process, so counting it
     * would retire an entry that was never actually tried. Such a rejection instead
     * {@linkplain RoleSyncOutboxEntry#asDeferred defers} the entry with its attempt counter
     * intact and suspends polling for {@link #CIRCUIT_PAUSE_SECONDS}, after which a single
     * probe row ({@link #PROBE_BATCH_SIZE}) tests the circuit before full batches resume.
     * Without this, an unreachable Kernel emptied the queue into {@code DEAD} at roughly
     * twenty rows a minute (26 November 2026).
     */
    private Mono<Void> handleFailure(RoleSyncOutboxEntry entry, Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        if (isCircuitRejection(error)) {
            circuitRefusedThisCycle.set(true);
            probing.set(true);
            LocalDateTime resumeAt = LocalDateTime.now().plusSeconds(CIRCUIT_PAUSE_SECONDS);
            pausedUntil.accumulateAndGet(resumeAt,
                    (current, candidate) -> current == null || current.isBefore(candidate) ? candidate : current);
            log.warn("Kernel circuit refused the call for outbox entry {} (operation={}) — the call never left "
                            + "this process, so the attempt is NOT counted; entry deferred to {}: {}",
                    entry.id(), entry.operation(), resumeAt, message);
            return outboxRepository.save(entry.asDeferred(message, resumeAt)).then();
        }

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

    /**
     * Whether the failure is the circuit breaker refusing to let the call through, rather
     * than the Kernel answering badly. Matched by <em>type</em> anywhere in the cause chain
     * — Reactor and the WebClient filter chain routinely wrap the original throwable, and
     * matching on the class name would quietly stop working if the exception were renamed.
     */
    static boolean isCircuitRejection(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof CallNotPermittedException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static Set<String> toStringSet(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        arrayNode.forEach(node -> result.add(node.asText()));
        return result;
    }
}
