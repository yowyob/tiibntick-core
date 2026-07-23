package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/admin/roles/assignments}.
 *
 * <p>{@code scopeId} is required for every role except {@code TNT_ADMIN}, whose scope
 * is always TiiBnTick's configured system tenant — see {@code TntRoleAssignmentService}.
 *
 * <p>{@code tenantId} is the caller's actual tenant, recorded on the local assignment row
 * so {@code LocalReactivePermissionResolver} can resolve permissions by
 * {@code (tenantId, userId)} later — distinct from {@code scopeId}, which for
 * AGENCY/ORGANIZATION-scoped roles is an agency/org id, not a tenant id. Ignored for
 * {@code TNT_ADMIN}, whose tenant is always TiiBnTick's configured system tenant.
 *
 * @author MANFOUO Braun
 */
public record AssignTntRoleRequest(
        UUID tenantId,
        UUID targetUserId,
        String roleCode,
        UUID scopeId
) {
}
