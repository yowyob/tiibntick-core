package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port: assigns an already-provisioned TiiBnTick role
 * (see {@link ITntRoleProvisioningPort}) to a specific Kernel user.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.roles.adapter.out.kernel.KernelRoleAssignmentAdapter}
 * which calls the Kernel's {@code POST /api/roles/assignments}.
 *
 * @author MANFOUO Braun
 */
public interface ITntRoleAssignmentPort {

    /**
     * Assigns role {@code roleCode} to {@code targetUserId} within the given scope.
     *
     * @param targetUserId Kernel user/actor id to grant the role to
     * @param roleCode     TiiBnTick role code (see {@code TntRole})
     * @param scopeType    Kernel scope type the role applies within (SYSTEM/TENANT/ORGANIZATION/AGENCY)
     * @param scopeId      id of the scope instance (system tenant id, tenant id, org id or agency id)
     * @return the Kernel-assigned assignment id
     */
    Mono<UUID> assignRole(UUID targetUserId, String roleCode, String scopeType, UUID scopeId);

    /**
     * Assigns a TiiBnTick role to a user inside <strong>the tenant of the calling JWT</strong>,
     * using the caller's own bearer token (relayed by the Kernel web client).
     *
     * <p>The Kernel resolves the tenant from the token's {@code tid} claim, not from any header,
     * so a role definition that only exists in the system tenant is invisible to a user working
     * in another tenant. This operation therefore looks the role up in the caller's tenant,
     * creates it there from {@code definition} when it is missing (on-demand tenant
     * provisioning, idempotent), and then grants it. An assignment that already exists is
     * returned as is.
     *
     * <p>It needs a JWT in the reactive security context: the scheduled outbox worker has none,
     * which is why role grants made only through the outbox never reached the Kernel.
     *
     * @return the Kernel-side assignment id
     */
    default Mono<UUID> assignRoleInCallerTenant(UUID targetUserId, TntRoleDefinition definition,
                                                String scopeType, UUID scopeId) {
        return Mono.error(new UnsupportedOperationException(
                "assignRoleInCallerTenant is not supported by this adapter"));
    }

    /**
     * Revokes a previously granted role assignment.
     *
     * @param kernelAssignmentId the Kernel-side assignment UUID (see {@link ITntRoleAssignmentPort#assignRole})
     */
    Mono<Void> revokeAssignment(UUID kernelAssignmentId);

    /**
     * Checks whether a role assignment known to still be provisioned in the Kernel (by its
     * Kernel-side id) actually still exists there. Used by
     * {@code KernelRoleReconciliationJob} — the Kernel exposes no
     * {@code GET /api/roles/assignments/{id}}, only {@code GET /api/roles/assignments?userId=},
     * so this lists the target user's assignments and checks whether {@code kernelAssignmentId}
     * is among them; scoped to a single already-known user, never a broad enumeration.
     *
     * @param userId             Kernel user/actor id the assignment was granted to
     * @param kernelAssignmentId the Kernel-side assignment UUID
     * @return {@code true} if the assignment is present in the Kernel's list for that user.
     *         On any error (Kernel unreachable, etc.) resolves to {@code true} — fail-safe,
     *         same reasoning as {@link ITntRoleProvisioningPort#roleExistsById}.
     */
    Mono<Boolean> assignmentExists(UUID userId, UUID kernelAssignmentId);
}
