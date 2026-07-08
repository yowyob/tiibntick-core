package com.yowyob.tiibntick.common.tenant;

import java.util.UUID;

/**
 * Identity/tenant coordinates resolved for the current request.
 *
 * <p>Local replacement for the Kernel's {@code TenantContext} — TiiBnTick no longer
 * shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is HTTP-only).
 * Produced from {@code TntSecurityContext} (tnt-auth-core) by the bootstrap module's
 * {@code CurrentTenantUseCase} implementation.
 *
 * @author MANFOUO Braun
 */
public record TenantContext(
        UUID tenantId,
        UUID organizationId,
        UUID agencyId,
        UUID userId,
        UUID actorId
) {
}
