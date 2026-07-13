package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/admin/roles/assignments}.
 *
 * @author MANFOUO Braun
 */
public record TntRoleAssignmentResponse(
        UUID assignmentId,
        UUID targetUserId,
        String roleCode,
        String scopeType,
        UUID scopeId
) {
    public static TntRoleAssignmentResponse from(TntRoleAssignmentResult result) {
        return new TntRoleAssignmentResponse(
                result.assignmentId(), result.targetUserId(), result.roleCode(),
                result.scopeType(), result.scopeId());
    }
}
