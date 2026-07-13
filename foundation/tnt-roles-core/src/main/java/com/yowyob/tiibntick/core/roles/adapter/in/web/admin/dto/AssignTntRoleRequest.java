package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/admin/roles/assignments}.
 *
 * <p>{@code scopeId} is required for every role except {@code TNT_ADMIN}, whose scope
 * is always TiiBnTick's configured system tenant — see {@code TntRoleAssignmentService}.
 *
 * @author MANFOUO Braun
 */
public record AssignTntRoleRequest(
        UUID targetUserId,
        String roleCode,
        UUID scopeId
) {
}
