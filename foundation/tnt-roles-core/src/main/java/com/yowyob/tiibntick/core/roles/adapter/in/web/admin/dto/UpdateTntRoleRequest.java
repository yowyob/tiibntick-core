package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/admin/roles/{roleId}}.
 *
 * <p>{@code tenantId} scopes the lookup — a role can only be edited by an admin acting
 * within the tenant it was created for. Local-only: no Kernel sync (see
 * {@code TntRoleManagementService#updateRole}). Rejected if {@code roleId} resolves to one
 * of the 9 canonical, system-owned roles.
 *
 * @author MANFOUO Braun
 */
public record UpdateTntRoleRequest(
        UUID tenantId,
        String name,
        Set<String> permissions
) {
}
