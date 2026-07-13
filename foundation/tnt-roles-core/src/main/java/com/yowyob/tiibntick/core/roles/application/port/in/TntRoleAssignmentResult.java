package com.yowyob.tiibntick.core.roles.application.port.in;

import java.util.UUID;

/**
 * Result of {@link AssignTntRoleUseCase#assignRole(UUID, String, UUID)}.
 *
 * @author MANFOUO Braun
 */
public record TntRoleAssignmentResult(
        UUID assignmentId,
        UUID targetUserId,
        String roleCode,
        String scopeType,
        UUID scopeId
) {
}
