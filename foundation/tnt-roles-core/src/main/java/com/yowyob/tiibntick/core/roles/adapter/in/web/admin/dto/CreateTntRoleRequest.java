package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;

import java.util.Set;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/admin/roles}.
 *
 * <p>{@code code} must not collide with one of the 9 canonical {@code TntRole} codes —
 * those are system-owned and not creatable through this endpoint (see
 * {@code TntRoleManagementService#createRole}).
 *
 * @author MANFOUO Braun
 */
public record CreateTntRoleRequest(
        UUID tenantId,
        String code,
        String name,
        RoleScopeType scopeType,
        Set<String> permissions
) {
}
