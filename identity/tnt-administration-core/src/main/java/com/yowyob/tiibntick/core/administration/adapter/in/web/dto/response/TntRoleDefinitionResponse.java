package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * API response DTO for a provisioned TntRoleDefinition.
 *
 * <p>Exposes the {@code kernelRoleId} — the UUID of the corresponding role in the Yowyob
 * Kernel — so that clients can cross-reference with the Kernel's role management APIs.
 *
 * @author MANFOUO Braun
 */
public record TntRoleDefinitionResponse(
        UUID id,
        UUID tenantId,
        String templateCode,
        String name,
        String scopeType,
        Set<String> permissionCodes,
        boolean protectedDefinition,
        /** Kernel role UUID (RT-comops-roles-core). Null until Kernel confirms provisioning. */
        UUID kernelRoleId,
        /** True when the Kernel has confirmed role creation and kernelRoleId is set. */
        boolean kernelSynced,
        Instant createdAt,
        Instant updatedAt
) {
    public static TntRoleDefinitionResponse from(TntRoleDefinition d) {
        return new TntRoleDefinitionResponse(
                d.getId(), d.getTenantId(), d.getTemplateCode(), d.getName(),
                d.getScopeType(), d.getPermissionCodes(), d.isProtectedDefinition(),
                d.getKernelRoleId(), d.isKernelSynced(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
