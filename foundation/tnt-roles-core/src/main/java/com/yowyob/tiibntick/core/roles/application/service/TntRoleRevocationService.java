package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.in.RevokeTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link RevokeTntRoleUseCase} by deleting the local {@code UserRoleAssignment}
 * row — this service no longer calls the Kernel directly (Chantier D · Audit n°6 · S5:
 * local RBAC persistence).
 *
 * <p>Enqueues {@code RoleSyncOutboxEntry(REVOKE_ASSIGNMENT)} only when
 * {@link UserRoleAssignmentRepository#findKernelAssignmentId} finds a recorded Kernel-side id
 * for the assignment being revoked — an assignment never successfully synced yet (or running
 * against the non-persistent in-memory fallback, which never records one) has nothing to
 * revoke Kernel-side, so only the local row is removed.
 *
 * @author MANFOUO Braun
 */
public class TntRoleRevocationService implements RevokeTntRoleUseCase {

    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleSyncOutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;
    private final IPermissionChangeNotifier permissionChangeNotifier;

    public TntRoleRevocationService(UserRoleAssignmentRepository assignmentRepository,
                                     RoleSyncOutboxRepository outboxRepository,
                                     TransactionalOperator transactionalOperator,
                                     ObjectMapper objectMapper,
                                     IPermissionChangeNotifier permissionChangeNotifier) {
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = objectMapper;
        this.permissionChangeNotifier = permissionChangeNotifier;
    }

    @Override
    public Mono<Void> revokeAssignment(UUID tenantId, UUID assignmentId) {
        return assignmentRepository.findById(tenantId, assignmentId)
                .switchIfEmpty(Mono.error(TntRoleException.assignmentNotFound(tenantId, assignmentId)))
                .flatMap(assignment ->
                        // Enqueue REVOKE_ASSIGNMENT only if the assignment ever got a Kernel-side id;
                        // either way .then(...) waits for that (possibly no-op) step before deleting
                        // locally — deleteById runs exactly once regardless of which branch fired.
                        assignmentRepository.findKernelAssignmentId(tenantId, assignmentId)
                                .flatMap(kernelAssignmentId -> outboxRepository.save(revokeOutboxEntry(tenantId, assignmentId, kernelAssignmentId)))
                                .then(assignmentRepository.deleteById(tenantId, assignmentId))
                                .doOnSuccess(v -> permissionChangeNotifier.notifyChanged(tenantId, assignment.userId())))
                .as(transactionalOperator::transactional);
    }

    private RoleSyncOutboxEntry revokeOutboxEntry(UUID tenantId, UUID assignmentId, UUID kernelAssignmentId) {
        String payload = RoleSyncPayloads.toJson(objectMapper, new RoleSyncPayloads.RevokeAssignmentPayload(kernelAssignmentId));
        return RoleSyncOutboxEntry.pending(RoleSyncOperation.REVOKE_ASSIGNMENT, RoleSyncAggregateType.ASSIGNMENT, assignmentId, tenantId, payload);
    }
}
