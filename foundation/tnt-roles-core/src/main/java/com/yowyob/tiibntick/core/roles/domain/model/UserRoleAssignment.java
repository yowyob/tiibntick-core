package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.UUID;

/**
 * Assignment of a {@link Role} to a user, optionally scoped to an agency/organization.
 *
 * <p>Local replacement for the Kernel's {@code UserRoleAssignment} domain type —
 * TiiBnTick no longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}:
 * Kernel is HTTP-only). Resolved via {@link
 * com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository}.
 *
 * @author MANFOUO Braun
 */
public record UserRoleAssignment(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID roleId,
        RoleScopeType scopeType,
        UUID scopeId
) {

    public static UserRoleAssignment assign(UUID tenantId, UUID userId, UUID roleId, RoleScopeType scopeType, UUID scopeId) {
        return new UserRoleAssignment(UUID.randomUUID(), tenantId, userId, roleId, scopeType, scopeId);
    }
}
