package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * Scope a {@link Role} / {@link UserRoleAssignment} applies within.
 *
 * <p>Local replacement for the Kernel's {@code RoleScopeType} — TiiBnTick no longer
 * shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public enum RoleScopeType {
    SYSTEM,
    TENANT,
    ORGANIZATION,
    AGENCY
}
