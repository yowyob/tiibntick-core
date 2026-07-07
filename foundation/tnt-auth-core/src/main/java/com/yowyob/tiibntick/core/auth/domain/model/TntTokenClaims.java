package com.yowyob.tiibntick.core.auth.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable value object representing the validated claims extracted from a TiiBnTick JWT.
 * Built from the Kernel's UserSessionTokenClaims — provides a clean domain boundary
 * so upstream TiiBnTick modules never depend on the Kernel's internal token types.
 *
 * @author MANFOUO Braun
 */
public record TntTokenClaims(
        UUID userId,
        UUID tenantId,
        UUID actorId,
        UUID organizationId,
        UUID agencyId,
        Set<String> permissions,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId
) {

    public TntTokenClaims {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    public boolean hasActorId() {
        return actorId != null;
    }
}
