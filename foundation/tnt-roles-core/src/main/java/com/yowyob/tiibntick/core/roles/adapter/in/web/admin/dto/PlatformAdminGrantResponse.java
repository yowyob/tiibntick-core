package com.yowyob.tiibntick.core.roles.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;

import java.time.Instant;
import java.util.UUID;

/**
 * One TNT_ADMIN grant as exposed by {@code /api/v1/admin/platform-admins} (decisions D-159 to D-175).
 * {@code protectedGrant} tells the administration platform not to offer a revocation (D-163).
 *
 * @author MANFOUO Braun
 */
public record PlatformAdminGrantResponse(
        UUID id, String roleCode, UUID userId, String grantKind, UUID grantedBy, UUID sourceTenantId,
        UUID systemTenantAtGrant, boolean sourceIsSystemTenant, boolean protectedGrant, boolean active,
        Instant grantedAt, Instant revokedAt, UUID revokedBy, String revokeReason, UUID revokeBatchId) {

    public static PlatformAdminGrantResponse of(PlatformRoleGrant g, UUID systemTenantId) {
        return new PlatformAdminGrantResponse(g.id(), g.roleCode(), g.userId(), g.kind().name(), g.grantedBy(),
                g.sourceTenantId(), g.systemTenantAtGrant(), systemTenantId.equals(g.sourceTenantId()),
                g.protectedFor(systemTenantId), g.active(), g.grantedAt(), g.revokedAt(), g.revokedBy(),
                g.revokeReason(), g.revokeBatchId());
    }
}
