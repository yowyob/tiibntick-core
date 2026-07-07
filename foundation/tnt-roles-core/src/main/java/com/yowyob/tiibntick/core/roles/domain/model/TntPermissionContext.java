package com.yowyob.tiibntick.core.roles.domain.model;

import java.util.UUID;

/**
 * Immutable context for evaluating TiiBnTick RBAC permissions.
 *
 * <p>Carries the identity fields needed by
 * {@link com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator}
 * to resolve and check permissions. Decouples the evaluator from any web/security
 * framework type (no dependency on {@code ApiKeyAuthenticationToken} or Spring Security).
 *
 * @author MANFOUO Braun
 */
public record TntPermissionContext(
        UUID userId,
        UUID tenantId,
        UUID agencyId,
        UUID actorId
) {

    public TntPermissionContext {
        if (userId == null) throw new IllegalArgumentException("userId is required for permission evaluation");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required for permission evaluation");
    }

    public static TntPermissionContext of(UUID userId, UUID tenantId) {
        return new TntPermissionContext(userId, tenantId, null, null);
    }

    public static TntPermissionContext of(UUID userId, UUID tenantId, UUID agencyId) {
        return new TntPermissionContext(userId, tenantId, agencyId, null);
    }

    public static TntPermissionContext full(UUID userId, UUID tenantId, UUID agencyId, UUID actorId) {
        return new TntPermissionContext(userId, tenantId, agencyId, actorId);
    }

    public boolean hasAgencyScope() {
        return agencyId != null;
    }
}
