package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, non-persistent {@link UserRoleAssignmentRepository} — process-lifetime
 * fallback used when no other bean provides the port (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class InMemoryUserRoleAssignmentRepository implements UserRoleAssignmentRepository {

    private final Map<UUID, UserRoleAssignment> assignments = new ConcurrentHashMap<>();

    @Override
    public Mono<UserRoleAssignment> save(UserRoleAssignment assignment) {
        return Mono.fromSupplier(() -> {
            assignments.put(assignment.id(), assignment);
            return assignment;
        });
    }

    @Override
    public Flux<UserRoleAssignment> findByTenantIdAndUserId(UUID tenantId, UUID userId) {
        return Flux.fromIterable(assignments.values())
                .filter(a -> tenantId.equals(a.tenantId()) && userId.equals(a.userId()));
    }

    @Override
    public Mono<UserRoleAssignment> findById(UUID tenantId, UUID assignmentId) {
        return Mono.justOrEmpty(assignments.get(assignmentId))
                .filter(a -> tenantId.equals(a.tenantId()));
    }

    @Override
    public Flux<UserRoleAssignment> findByTenantIdAndRoleId(UUID tenantId, UUID roleId) {
        return Flux.fromIterable(assignments.values())
                .filter(a -> tenantId.equals(a.tenantId()) && roleId.equals(a.roleId()));
    }

    @Override
    public Mono<Void> deleteById(UUID tenantId, UUID assignmentId) {
        return Mono.fromRunnable(() -> assignments.computeIfPresent(assignmentId,
                (id, existing) -> tenantId.equals(existing.tenantId()) ? null : existing));
    }

    /**
     * No-op — see {@code InMemoryRoleRepository#markKernelRoleId} for the rationale:
     * {@link UserRoleAssignment} carries no {@code kernelAssignmentId} field, so this
     * process-lifetime fallback has nowhere to durably record it.
     */
    @Override
    public Mono<Void> markKernelAssignmentId(UUID tenantId, UUID assignmentId, UUID kernelAssignmentId) {
        return Mono.empty();
    }

    /** Always empty — see {@link #markKernelAssignmentId}: nothing was ever durably recorded. */
    @Override
    public Mono<UUID> findKernelAssignmentId(UUID tenantId, UUID assignmentId) {
        return Mono.empty();
    }
}
