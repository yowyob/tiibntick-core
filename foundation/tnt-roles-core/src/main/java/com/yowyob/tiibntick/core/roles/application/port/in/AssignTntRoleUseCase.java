package com.yowyob.tiibntick.core.roles.application.port.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Primary (inbound) port: grants one of TiiBnTick's canonical roles (see {@code TntRole})
 * to a Kernel user. Reserved for callers already holding {@code system:admin} —
 * see {@code TntPermission.SYSTEM_ADMIN} and the {@code TNT_ADMIN} bootstrap flow in
 * {@code docs/auth/platform-client-onboarding-guide.md}.
 *
 * @author MANFOUO Braun
 */
public interface AssignTntRoleUseCase {

    /**
     * Assigns {@code roleCode} to {@code targetUserId}.
     *
     * @param targetUserId Kernel user/actor id to grant the role to
     * @param roleCode     TiiBnTick role code (e.g. "TNT_ADMIN", "AGENCY_MANAGER")
     * @param scopeId      id of the scope instance the role applies within (tenant/org/agency id);
     *                     ignored for SYSTEM-scoped roles (TNT_ADMIN), whose scope is the
     *                     configured system tenant regardless of what is passed here
     * @return the resulting assignment, including the scope actually used
     */
    Mono<TntRoleAssignmentResult> assignRole(UUID targetUserId, String roleCode, UUID scopeId);
}
