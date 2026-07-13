package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link AssignTntRoleUseCase} by resolving the target role's scope and
 * delegating the actual Kernel call to {@link ITntRoleAssignmentPort}.
 *
 * <p>SYSTEM-scoped roles (only {@code TNT_ADMIN} today) are always assigned within
 * TiiBnTick's system tenant ({@code tnt.roles.system-tenant-id}) regardless of the
 * caller-supplied {@code scopeId} — this is what makes the resulting
 * {@code ROLE_TNT_ADMIN} authority meaningful once
 * {@code TntSecurityConfig#tntJwtAuthenticationConverter} checks the JWT's {@code tid}
 * claim against that same tenant.
 *
 * @author MANFOUO Braun
 */
public class TntRoleAssignmentService implements AssignTntRoleUseCase {

    private final ITntRoleAssignmentPort assignmentPort;
    private final ITntRoleProvisioningPort provisioningPort;
    private final UUID systemTenantId;

    public TntRoleAssignmentService(ITntRoleAssignmentPort assignmentPort,
                                     ITntRoleProvisioningPort provisioningPort,
                                     UUID systemTenantId) {
        this.assignmentPort = assignmentPort;
        this.provisioningPort = provisioningPort;
        this.systemTenantId = systemTenantId;
    }

    @Override
    public Mono<TntRoleAssignmentResult> assignRole(UUID targetUserId, String roleCode, UUID scopeId) {
        if (!TntRole.isKnownRole(roleCode)) {
            return Mono.error(TntRoleException.unknownRole(roleCode));
        }
        TntRole role = TntRole.fromCode(roleCode);
        String scopeType = role.scopeType().name();
        UUID resolvedScopeId = role.scopeType() == RoleScopeType.SYSTEM ? systemTenantId : scopeId;
        if (resolvedScopeId == null) {
            return Mono.error(TntRoleException.missingScopeId(role.code(), scopeType));
        }

        return assignmentPort.assignRole(targetUserId, role.code(), scopeType, resolvedScopeId)
                .flatMap(assignmentId -> provisioningPort.invalidatePermissionCache(resolvedScopeId, targetUserId)
                        .thenReturn(new TntRoleAssignmentResult(
                                assignmentId, targetUserId, role.code(), scopeType, resolvedScopeId)));
    }
}
