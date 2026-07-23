package com.yowyob.tiibntick.core.roles.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.RoleEntity;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.entity.UserRoleAssignmentEntity;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hand-written entity/domain mapper for the {@code tnt-roles-core} persistence layer —
 * plain field mapping, no MapStruct, same style as {@code tnt-platform-gateway-core}'s
 * {@code PlatformClientPersistenceMapper}.
 *
 * @author MANFOUO Braun
 */
public final class RolePersistenceMapper {

    private RolePersistenceMapper() {
    }

    // ── Role ─────────────────────────────────────────────────────────────────

    public static Role toDomain(RoleEntity e) {
        if (e == null) return null;
        return new Role(
                e.getId(),
                e.getTenantId(),
                e.getCode(),
                e.getName(),
                RoleScopeType.valueOf(e.getScopeType()),
                splitPermissions(e.getPermissions()));
    }

    /**
     * Builds a brand-new {@link RoleEntity} for a domain {@link Role} that has no row yet —
     * defaults the columns the domain object doesn't carry ({@code systemRole=false},
     * {@code editable=true}, {@code kernelRoleId=null}) and stamps both timestamps to now.
     */
    public static RoleEntity toNewEntity(Role d) {
        RoleEntity e = new RoleEntity();
        e.markNew();
        e.setId(d.id());
        applyDomainFields(e, d);
        e.setSystemRole(false);
        e.setEditable(true);
        e.setKernelRoleId(null);
        java.time.Instant now = java.time.Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    /**
     * Applies the domain-carried fields of {@code d} onto an existing {@code e} fetched from
     * the DB — preserves {@code systemRole}/{@code editable}/{@code kernelRoleId}/
     * {@code createdAt} (columns the domain {@link Role} record doesn't expose) and bumps
     * {@code updatedAt}.
     */
    public static void applyDomainFieldsForUpdate(RoleEntity e, Role d) {
        applyDomainFields(e, d);
        e.setUpdatedAt(java.time.Instant.now());
    }

    private static void applyDomainFields(RoleEntity e, Role d) {
        e.setTenantId(d.tenantId());
        e.setCode(d.code());
        e.setName(d.name());
        e.setScopeType(d.scopeType().name());
        e.setPermissions(joinPermissions(d.permissions()));
    }

    static String joinPermissions(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return "";
        return String.join(",", permissions);
    }

    static Set<String> splitPermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) return Set.of();
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ── UserRoleAssignment ───────────────────────────────────────────────────

    public static UserRoleAssignment toDomain(UserRoleAssignmentEntity e) {
        if (e == null) return null;
        return new UserRoleAssignment(
                e.getId(),
                e.getTenantId(),
                e.getUserId(),
                e.getRoleId(),
                RoleScopeType.valueOf(e.getScopeType()),
                e.getScopeId());
    }

    /**
     * Builds a brand-new {@link UserRoleAssignmentEntity} — {@code kernelAssignmentId}
     * defaults to {@code null} (the domain {@link UserRoleAssignment} record doesn't carry
     * it) and {@code createdAt} is stamped to now.
     */
    public static UserRoleAssignmentEntity toNewEntity(UserRoleAssignment d) {
        UserRoleAssignmentEntity e = new UserRoleAssignmentEntity();
        e.markNew();
        e.setId(d.id());
        e.setTenantId(d.tenantId());
        e.setUserId(d.userId());
        e.setRoleId(d.roleId());
        e.setScopeType(d.scopeType().name());
        e.setScopeId(d.scopeId());
        e.setKernelAssignmentId(null);
        e.setCreatedAt(java.time.Instant.now());
        return e;
    }
}
