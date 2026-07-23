package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.in.ManageTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Implements {@link ManageTntRoleUseCase} — CRUD for tenant-defined custom (non-system)
 * roles, writing locally and enqueueing {@code RoleSyncOutboxEntry} rows for asynchronous
 * Kernel sync where applicable (Chantier D · Audit n°6 · S5: local RBAC persistence). This
 * service no longer calls the Kernel directly.
 *
 * <p>{@code deleteRole} enqueues {@code RoleSyncOutboxEntry(DELETE_ROLE)} only when
 * {@link RoleRepository#findKernelRoleId} finds a recorded Kernel-side id for the role being
 * deleted — a role never successfully synced yet (or running against the non-persistent
 * in-memory fallback, which never records one) has nothing to delete Kernel-side, so only the
 * local row is removed.
 *
 * @author MANFOUO Braun
 */
public class TntRoleManagementService implements ManageTntRoleUseCase {

    private final RoleRepository roleRepository;
    private final RoleSyncOutboxRepository outboxRepository;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;

    public TntRoleManagementService(RoleRepository roleRepository,
                                     RoleSyncOutboxRepository outboxRepository,
                                     TransactionalOperator transactionalOperator,
                                     ObjectMapper objectMapper) {
        this.roleRepository = roleRepository;
        this.outboxRepository = outboxRepository;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Role> createRole(UUID tenantId, String code, String name, RoleScopeType scopeType, Set<String> permissions) {
        if (TntRole.isKnownRole(code)) {
            return Mono.error(TntRoleException.roleCodeReserved(code));
        }

        Role role = Role.create(tenantId, code, name, scopeType, permissions);
        String payload = RoleSyncPayloads.toJson(objectMapper,
                new RoleSyncPayloads.ProvisionRolePayload(tenantId, role.code(), role.name(), role.scopeType().name(), role.permissions()));
        RoleSyncOutboxEntry outboxEntry = RoleSyncOutboxEntry.pending(
                RoleSyncOperation.PROVISION_ROLE, RoleSyncAggregateType.ROLE, role.id(), tenantId, payload);

        return roleRepository.save(role)
                .flatMap(saved -> outboxRepository.save(outboxEntry).thenReturn(saved))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Role> updateRole(UUID tenantId, UUID roleId, String name, Set<String> permissions) {
        // Local-only by explicit product decision — no outbox entry, see class Javadoc.
        return roleRepository.findById(tenantId, roleId)
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFound(tenantId, roleId)))
                .flatMap(existing -> {
                    if (TntRole.isKnownRole(existing.code())) {
                        return Mono.error(TntRoleException.systemRoleNotEditable(existing.code()));
                    }
                    Role updated = new Role(existing.id(), existing.tenantId(), existing.code(), name, existing.scopeType(), permissions);
                    return roleRepository.save(updated);
                });
    }

    @Override
    public Mono<Void> deleteRole(UUID tenantId, UUID roleId) {
        return roleRepository.findById(tenantId, roleId)
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFound(tenantId, roleId)))
                .flatMap(existing -> {
                    if (TntRole.isKnownRole(existing.code())) {
                        return Mono.error(TntRoleException.systemRoleNotEditable(existing.code()));
                    }
                    // Enqueue DELETE_ROLE only if the role ever got a Kernel-side id; either way,
                    // .then(...) waits for that (possibly no-op) step before deleting locally —
                    // deleteById runs exactly once regardless of which branch fired.
                    return roleRepository.findKernelRoleId(tenantId, roleId)
                            .flatMap(kernelRoleId -> outboxRepository.save(deleteRoleOutboxEntry(tenantId, roleId, kernelRoleId)))
                            .then(roleRepository.deleteById(tenantId, roleId));
                })
                .as(transactionalOperator::transactional);
    }

    private RoleSyncOutboxEntry deleteRoleOutboxEntry(UUID tenantId, UUID roleId, UUID kernelRoleId) {
        String payload = RoleSyncPayloads.toJson(objectMapper, new RoleSyncPayloads.DeleteRolePayload(tenantId, kernelRoleId));
        return RoleSyncOutboxEntry.pending(RoleSyncOperation.DELETE_ROLE, RoleSyncAggregateType.ROLE, roleId, tenantId, payload);
    }

    @Override
    public Flux<Role> listRoles(UUID tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }
}
