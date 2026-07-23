package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spot-checks the Kernel-side counterpart of local RBAC rows we already know were
 * successfully provisioned, and re-enqueues a fresh {@code PROVISION_ROLE}/{@code ASSIGN_ROLE}
 * outbox entry for anything found missing (Chantier D · Audit n°6 · S5).
 *
 * <p><b>Scope constraint (deliberate, product-owner mandated):</b> this job never pulls or
 * diffs the Kernel's full role/assignment list — the Kernel hosts roles from other Yowyob
 * solutions that must not be enumerated or touched here. It only ever asks the Kernel about
 * ids TiiBnTick already knows about: the {@link RoleSyncOutboxRepository#findByStatus}
 * {@link RoleSyncStatus#PROVISIONED PROVISIONED} rows are the durable record of "a local
 * role/assignment once received a Kernel-side id" (the domain {@link Role}/
 * {@link UserRoleAssignment} records themselves carry no such field — see
 * {@link RoleRepository#markKernelRoleId}). {@code DELETE_ROLE}/{@code REVOKE_ASSIGNMENT}
 * entries are filtered out: those record an intentional removal, so their target being gone
 * from the Kernel is the expected, correct state — nothing to reconcile.
 *
 * <p>Same {@link SchedulerLock}/{@link AtomicBoolean} guard shape as
 * {@link KernelRoleSyncWorker} and {@code yow-event-kernel}'s {@code OutboxPollerService}.
 *
 * <p>Not registered as a Spring bean here — instantiated explicitly by a later wiring phase
 * (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class KernelRoleReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleReconciliationJob.class);

    private final RoleSyncOutboxRepository outboxRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final ITntRoleProvisioningPort provisioningPort;
    private final ITntRoleAssignmentPort assignmentPort;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean runInProgress = new AtomicBoolean(false);

    public KernelRoleReconciliationJob(RoleSyncOutboxRepository outboxRepository,
                                        RoleRepository roleRepository,
                                        UserRoleAssignmentRepository assignmentRepository,
                                        ITntRoleProvisioningPort provisioningPort,
                                        ITntRoleAssignmentPort assignmentPort,
                                        ObjectMapper objectMapper) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.provisioningPort = Objects.requireNonNull(provisioningPort);
        this.assignmentPort = Objects.requireNonNull(assignmentPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Scheduled(cron = "${tnt.roles.reconciliation.cron:0 0 * * * *}")
    @SchedulerLock(name = "tnt-roles-kernel-reconciliation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void scheduledReconcile() {
        LockAssert.assertLocked();
        reconcile().subscribe(
                count -> {
                    if (count > 0) {
                        log.info("Role reconciliation cycle completed: {} entries re-enqueued", count);
                    }
                },
                error -> log.error("Role reconciliation cycle failed", error)
        );
    }

    /**
     * @return the number of stale outbox entries re-enqueued this cycle
     */
    public Mono<Integer> reconcile() {
        if (!runInProgress.compareAndSet(false, true)) {
            log.trace("Role reconciliation cycle skipped — previous cycle still running");
            return Mono.just(0);
        }
        return outboxRepository.findByStatus(RoleSyncStatus.PROVISIONED)
                .filter(entry -> entry.operation() == RoleSyncOperation.PROVISION_ROLE
                        || entry.operation() == RoleSyncOperation.ASSIGN_ROLE)
                .flatMap(this::reconcileEntry)
                // reconcileEntry always emits true/false (checked vs. re-enqueued) — only
                // count entries that actually needed re-enqueuing, not every entry checked.
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue)
                .doFinally(signal -> runInProgress.set(false));
    }

    private Mono<Boolean> reconcileEntry(RoleSyncOutboxEntry entry) {
        Mono<Boolean> result = switch (entry.aggregateType()) {
            case ROLE -> reconcileRole(entry);
            case ASSIGNMENT -> reconcileAssignment(entry);
        };
        return result.onErrorResume(error -> {
            log.warn("Reconciliation check failed for outbox entry {} (kept as-is, will retry next cycle): {}",
                    entry.id(), error.getMessage(), error);
            return Mono.just(false);
        });
    }

    // ── ROLE ──────────────────────────────────────────────────────────────────

    private Mono<Boolean> reconcileRole(RoleSyncOutboxEntry entry) {
        return provisioningPort.roleExistsById(entry.tenantId(), entry.kernelRefId())
                .flatMap(exists -> exists ? Mono.just(false) : reenqueueRole(entry));
    }

    private Mono<Boolean> reenqueueRole(RoleSyncOutboxEntry entry) {
        return roleRepository.findById(entry.tenantId(), entry.aggregateId())
                .flatMap(role -> {
                    log.warn("Role {} (kernel id {}) missing in Kernel — re-enqueuing PROVISION_ROLE",
                            role.id(), entry.kernelRefId());
                    RoleSyncOutboxEntry fresh = RoleSyncOutboxEntry.pending(
                            RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE,
                            role.id(), role.tenantId(), buildProvisionRolePayload(role));
                    return outboxRepository.save(fresh).thenReturn(true);
                })
                // No local row anymore (deleted since) — nothing left to re-provision. Note:
                // defaultIfEmpty, not switchIfEmpty(...).thenReturn(true) placed afterwards —
                // .thenReturn() on an empty upstream Mono still completes and would emit its
                // value regardless of emptiness, so the fallback must sit directly on the
                // (possibly empty) flatMap result, not after a value-injecting operator.
                .defaultIfEmpty(false);
    }

    private String buildProvisionRolePayload(Role role) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("tenantId", role.tenantId().toString());
            node.put("code", role.code());
            node.put("name", role.name());
            node.put("scopeType", role.scopeType().name());
            ArrayNode permissions = node.putArray("permissions");
            role.permissions().forEach(permissions::add);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize PROVISION_ROLE payload for role " + role.id(), e);
        }
    }

    // ── ASSIGNMENT ────────────────────────────────────────────────────────────

    private Mono<Boolean> reconcileAssignment(RoleSyncOutboxEntry entry) {
        return assignmentRepository.findById(entry.tenantId(), entry.aggregateId())
                .flatMap(assignment -> assignmentPort.assignmentExists(assignment.userId(), entry.kernelRefId())
                        .flatMap(exists -> exists ? Mono.just(false) : reenqueueAssignment(entry, assignment)))
                // No local row anymore (deleted since) — nothing left to re-provision.
                .switchIfEmpty(Mono.just(false));
    }

    private Mono<Boolean> reenqueueAssignment(RoleSyncOutboxEntry entry, UserRoleAssignment assignment) {
        return roleRepository.findById(entry.tenantId(), assignment.roleId())
                .flatMap(role -> {
                    log.warn("Assignment {} (kernel id {}) missing in Kernel — re-enqueuing ASSIGN_ROLE",
                            assignment.id(), entry.kernelRefId());
                    RoleSyncOutboxEntry fresh = RoleSyncOutboxEntry.pending(
                            RoleSyncOperation.ASSIGN_ROLE, RoleSyncAggregateType.ASSIGNMENT,
                            assignment.id(), assignment.tenantId(), buildAssignRolePayload(assignment, role));
                    return outboxRepository.save(fresh).thenReturn(true);
                })
                // The role behind this assignment is gone locally too — nothing to
                // re-provision. See reenqueueRole for why defaultIfEmpty, not switchIfEmpty
                // after thenReturn.
                .defaultIfEmpty(false);
    }

    private String buildAssignRolePayload(UserRoleAssignment assignment, Role role) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("userId", assignment.userId().toString());
            node.put("roleCode", role.code());
            node.put("scopeType", assignment.scopeType().name());
            node.put("scopeId", assignment.scopeId().toString());
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to serialize ASSIGN_ROLE payload for assignment " + assignment.id(), e);
        }
    }
}
