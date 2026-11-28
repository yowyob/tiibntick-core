package com.yowyob.tiibntick.core.roles.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One grant of a platform role (TNT_ADMIN today) in the Core registry {@code tnt_platform_role_grants}
 * (decision D-159). The role holds whatever tenant the holder logs into; the history is never deleted.
 *
 * @param sourceTenantId      the tenant the role was obtained from: the {@code tid} of the granter's token at
 *                            grant time (D-164); the system tenant for a {@link PlatformGrantKind#SYSTEM_OWNER}
 * @param systemTenantAtGrant the system tenant in force at grant time (audit)
 * @author MANFOUO Braun
 */
public record PlatformRoleGrant(
        UUID id,
        String roleCode,
        UUID userId,
        PlatformGrantKind kind,
        UUID grantedBy,
        UUID sourceTenantId,
        UUID systemTenantAtGrant,
        Instant grantedAt,
        Instant revokedAt,
        UUID revokedBy,
        String revokeReason,
        UUID revokeBatchId) {

    public static final String TNT_ADMIN = "TNT_ADMIN";

    public static PlatformRoleGrant newGrant(UUID userId, PlatformGrantKind kind, UUID grantedBy,
                                             UUID sourceTenantId, UUID systemTenantId, Instant now) {
        return new PlatformRoleGrant(UUID.randomUUID(), TNT_ADMIN, userId, kind, grantedBy, sourceTenantId,
                systemTenantId, now, null, null, null, null);
    }

    public boolean active() {
        return revokedAt == null;
    }

    /**
     * D-163: the system-tenant owner's grant cannot be revoked while it was obtained from the system tenant in
     * force; after a change of system tenant it becomes an ordinary foreign-tenant grant.
     */
    public boolean protectedFor(UUID currentSystemTenantId) {
        return active() && kind == PlatformGrantKind.SYSTEM_OWNER
                && currentSystemTenantId != null && currentSystemTenantId.equals(sourceTenantId);
    }

    /** D-165: the grants the mass revocation targets. */
    public boolean foreignTo(UUID currentSystemTenantId) {
        return active() && !currentSystemTenantId.equals(sourceTenantId);
    }
}
