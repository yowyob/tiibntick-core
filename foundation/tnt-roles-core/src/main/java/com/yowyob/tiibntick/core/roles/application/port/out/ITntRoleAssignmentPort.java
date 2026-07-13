package com.yowyob.tiibntick.core.roles.application.port.out;

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
}
