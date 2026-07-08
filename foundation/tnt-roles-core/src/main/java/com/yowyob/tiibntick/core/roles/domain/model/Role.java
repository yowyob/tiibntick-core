package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * A provisioned RBAC role: a code, its scope, and its granted permission set.
 *
 * <p>Local replacement for the Kernel's {@code Role} domain type — TiiBnTick no longer
 * shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 * Resolved via {@link com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository}.
 *
 * @author MANFOUO Braun
 */
public record Role(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        RoleScopeType scopeType,
        Set<String> permissions
) {

    public Role {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static Role create(UUID tenantId, String code, String name, RoleScopeType scopeType, Set<String> permissions) {
        return new Role(UUID.randomUUID(), tenantId, code, name, scopeType, permissions);
    }
}
