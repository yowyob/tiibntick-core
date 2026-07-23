package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.roles.domain.model.Role;

import java.util.Set;
import java.util.UUID;

/**
 * Response body for the role CRUD admin endpoints.
 *
 * @author MANFOUO Braun
 */
public record TntRoleResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String scopeType,
        Set<String> permissions
) {
    public static TntRoleResponse from(Role role) {
        return new TntRoleResponse(
                role.id(), role.tenantId(), role.code(), role.name(),
                role.scopeType().name(), role.permissions());
    }
}
