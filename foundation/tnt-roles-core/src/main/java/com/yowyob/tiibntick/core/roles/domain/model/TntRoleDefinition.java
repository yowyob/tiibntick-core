package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.Set;

/**
 * Immutable value object describing a TiiBnTick role as a provisioning template.
 *
 * <p>Built from {@link TntRole} enum entries by the
 * {@link com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry}.
 * Used by {@code tnt-administration-core} when bootstrapping roles for a new tenant.
 *
 * <p>Deliberately not a JPA/R2DBC entity — this is a pure domain value object.
 *
 * @author MANFOUO Braun
 */
public record TntRoleDefinition(
        String code,
        String name,
        String description,
        RoleScopeType scopeType,
        Set<String> defaultPermissions,
        boolean systemRole,
        boolean editable
) {

    public TntRoleDefinition {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (scopeType == null) throw new IllegalArgumentException("scopeType is required");
        defaultPermissions = defaultPermissions == null ? Set.of() : Set.copyOf(defaultPermissions);
    }

    /**
     * Factory: builds a TntRoleDefinition from a TntRole enum constant.
     */
    public static TntRoleDefinition from(TntRole role) {
        return new TntRoleDefinition(
                role.code(),
                role.label(),
                "Default TiiBnTick role: " + role.label(),
                role.scopeType(),
                role.defaultPermissions(),
                role.isSystemRole(),
                !role.isSystemRole()
        );
    }

    /**
     * Returns the Kernel-compatible scope type string for use with
     * {@code CreateRoleCommand.scopeType()}.
     */
    public String scopeTypeCode() {
        return scopeType.name();
    }
}
