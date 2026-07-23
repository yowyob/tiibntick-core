package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link UserRoleAssignment} persistence.
 *
 * <p>Local replacement for the Kernel's {@code UserRoleAssignmentRepository} port —
 * TiiBnTick no longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}:
 * Kernel is HTTP-only). Default implementation: {@link
 * com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryUserRoleAssignmentRepository}.
 *
 * @author MANFOUO Braun
 */
public interface UserRoleAssignmentRepository {

    Mono<UserRoleAssignment> save(UserRoleAssignment assignment);

    Flux<UserRoleAssignment> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Mono<UserRoleAssignment> findById(UUID tenantId, UUID assignmentId);

    Flux<UserRoleAssignment> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    Mono<Void> deleteById(UUID tenantId, UUID assignmentId);

    /**
     * Records the Kernel-side assignment id once {@link UserRoleAssignment} has been
     * successfully assigned in the Kernel (see {@code KernelRoleSyncWorker}).
     *
     * <p>Deliberately narrow — same rationale as {@link
     * com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository#markKernelRoleId}:
     * {@link UserRoleAssignment} itself carries no {@code kernelAssignmentId} field. A no-op
     * for non-persistent implementations, which have nowhere to durably record it.
     *
     * @param tenantId           tenant the assignment belongs to
     * @param assignmentId       local assignment id ({@link UserRoleAssignment#id()})
     * @param kernelAssignmentId the Kernel-side assignment UUID
     */
    Mono<Void> markKernelAssignmentId(UUID tenantId, UUID assignmentId, UUID kernelAssignmentId);

    /**
     * Reads back the Kernel-side assignment id recorded by {@link #markKernelAssignmentId},
     * if any. Empty means either the assignment was never successfully synced to the Kernel
     * yet, or (for non-persistent implementations) that no such record exists at all — both
     * cases mean "nothing to revoke Kernel-side" to a caller like
     * {@code TntRoleRevocationService}.
     */
    Mono<UUID> findKernelAssignmentId(UUID tenantId, UUID assignmentId);
}
