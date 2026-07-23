package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link AssignTntRoleUseCase} by writing the assignment to the local RBAC
 * store and enqueueing a {@code RoleSyncOutboxEntry} for asynchronous replay to the Kernel —
 * this service no longer calls the Kernel directly (Chantier D · Audit n°6 · S5: local RBAC
 * persistence). A separately built sync worker drains the outbox and performs the actual
 * Kernel HTTP call.
 *
 * <p>SYSTEM-scoped roles (only {@code TNT_ADMIN} today) are always assigned within
 * TiiBnTick's system tenant ({@code tnt.roles.system-tenant-id}) regardless of the
 * caller-supplied {@code tenantId}/{@code scopeId} — this is what makes the resulting
 * {@code ROLE_TNT_ADMIN} authority meaningful once
 * {@code TntSecurityConfig#tntJwtAuthenticationConverter} checks the JWT's {@code tid}
 * claim against that same tenant.
 *
 * @author MANFOUO Braun
 */
public class TntRoleAssignmentService implements AssignTntRoleUseCase {

    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleSyncOutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;
    private final UUID systemTenantId;
    private final IPermissionChangeNotifier permissionChangeNotifier;

    public TntRoleAssignmentService(RoleRepository roleRepository,
                                     UserRoleAssignmentRepository assignmentRepository,
                                     RoleSyncOutboxRepository outboxRepository,
                                     TransactionalOperator transactionalOperator,
                                     ObjectMapper objectMapper,
                                     UUID systemTenantId,
                                     IPermissionChangeNotifier permissionChangeNotifier) {
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxRepository = outboxRepository;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = objectMapper;
        this.systemTenantId = systemTenantId;
        this.permissionChangeNotifier = permissionChangeNotifier;
    }

    @Override
    public Mono<TntRoleAssignmentResult> assignRole(UUID tenantId, UUID targetUserId, String roleCode, UUID scopeId) {
        if (!TntRole.isKnownRole(roleCode)) {
            return Mono.error(TntRoleException.unknownRole(roleCode));
        }
        TntRole role = TntRole.fromCode(roleCode);
        RoleScopeType scopeType = role.scopeType();

        UUID resolvedScopeId = scopeType == RoleScopeType.SYSTEM ? systemTenantId : scopeId;
        if (resolvedScopeId == null) {
            return Mono.error(TntRoleException.missingScopeId(role.code(), scopeType.name()));
        }

        UUID resolvedTenantId = scopeType == RoleScopeType.SYSTEM ? systemTenantId : tenantId;
        if (resolvedTenantId == null) {
            return Mono.error(TntRoleException.missingTenantId(role.code()));
        }

        // Canonical role definitions live once, under the system tenant — same invariant as
        // TntRoleInitializationService, which is the only writer of these rows.
        return roleRepository.findByTenantId(systemTenantId)
                .filter(localRole -> localRole.code().equalsIgnoreCase(role.code()))
                .next()
                .switchIfEmpty(Mono.error(TntRoleException.roleNotSeeded(role.code())))
                .flatMap(localRole -> saveAssignmentAndEnqueue(resolvedTenantId, targetUserId, localRole.id(), role, scopeType, resolvedScopeId));
    }

    private Mono<TntRoleAssignmentResult> saveAssignmentAndEnqueue(UUID resolvedTenantId, UUID targetUserId, UUID localRoleId,
            TntRole role, RoleScopeType scopeType, UUID resolvedScopeId) {
        UserRoleAssignment assignment = UserRoleAssignment.assign(resolvedTenantId, targetUserId, localRoleId, scopeType, resolvedScopeId);

        String payload = RoleSyncPayloads.toJson(objectMapper,
                new RoleSyncPayloads.AssignRolePayload(targetUserId, role.code(), scopeType.name(), resolvedScopeId));
        RoleSyncOutboxEntry outboxEntry = RoleSyncOutboxEntry.pending(
                RoleSyncOperation.ASSIGN_ROLE, RoleSyncAggregateType.ASSIGNMENT, assignment.id(), resolvedTenantId, payload);

        return assignmentRepository.save(assignment)
                .flatMap(saved -> outboxRepository.save(outboxEntry).thenReturn(saved))
                .as(transactionalOperator::transactional)
                .doOnSuccess(saved -> permissionChangeNotifier.notifyChanged(resolvedTenantId, targetUserId))
                .map(saved -> new TntRoleAssignmentResult(
                        saved.id(), targetUserId, role.code(), scopeType.name(), resolvedScopeId));
    }
}
