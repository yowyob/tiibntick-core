package com.yowyob.tiibntick.core.roles.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary (inbound) port: grants one of TiiBnTick's canonical roles (see {@code TntRole})
 * to a Kernel user. Reserved for callers already holding {@code system:admin} —
 * see {@code TntPermission.SYSTEM_ADMIN} and the {@code TNT_ADMIN} bootstrap flow in
 * {@code docs/auth/platform-client-onboarding-guide.md}.
 *
 * <p>Local RBAC persistence (Chantier D · Audit n°6 · S5): the implementation writes the
 * assignment to the local {@code UserRoleAssignment} store and enqueues a
 * {@code RoleSyncOutboxEntry} for asynchronous replay to the Kernel — it no longer calls
 * the Kernel directly in-request.
 *
 * @author MANFOUO Braun
 */
public interface AssignTntRoleUseCase {

    /**
     * Assigns {@code roleCode} to {@code targetUserId}.
     *
     * @param tenantId     tenant the local assignment row belongs to — this is the caller's
     *                     actual JWT tenant, used later by {@code LocalReactivePermissionResolver}
     *                     to resolve permissions by {@code (tenantId, userId)}; it is distinct
     *                     from {@code scopeId}, which for AGENCY/ORGANIZATION-scoped roles is an
     *                     agency/org id, not a tenant id. Ignored for SYSTEM-scoped roles
     *                     (TNT_ADMIN), whose tenant is always the configured system tenant
     *                     regardless of what is passed here
     * @param targetUserId Kernel user/actor id to grant the role to
     * @param roleCode     TiiBnTick role code (e.g. "TNT_ADMIN", "AGENCY_MANAGER")
     * @param scopeId      id of the scope instance the role applies within (tenant/org/agency id);
     *                     ignored for SYSTEM-scoped roles (TNT_ADMIN), whose scope is the
     *                     configured system tenant regardless of what is passed here
     * @return the resulting assignment, including the scope actually used
     */
    Mono<TntRoleAssignmentResult> assignRole(UUID tenantId, UUID targetUserId, String roleCode, UUID scopeId);
}
