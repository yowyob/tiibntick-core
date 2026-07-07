package com.yowyob.tiibntick.core.auth.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * Lightweight projection of TntSecurityContext carrying only identity fields.
 * Injected into WebFlux controller parameters via the {@code @CurrentUser} annotation
 * when the full context is not required.
 *
 * <p>This is a stable API: controllers depend on TntUserIdentity, not on
 * ApiKeyAuthenticationToken from the Kernel — preserving the hexagonal boundary.
 *
 * @author MANFOUO Braun
 */
public record TntUserIdentity(
        UUID userId,
        UUID tenantId,
        UUID actorId,
        UUID organizationId,
        UUID agencyId,
        Set<String> permissions,
        boolean freelancer
) {

    public TntUserIdentity {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * Derives a TntUserIdentity from the full security context.
     */
    public static TntUserIdentity from(TntSecurityContext ctx) {
        return new TntUserIdentity(
                ctx.userId(),
                ctx.tenantId(),
                ctx.actorId(),
                ctx.organizationId(),
                ctx.agencyId(),
                ctx.permissions(),
                ctx.freelancer()
        );
    }

    public boolean hasPermission(String resource, String action) {
        String exact = resource + ":" + action;
        String wildcard = resource + ":*";
        return permissions.contains(exact) || permissions.contains(wildcard) || permissions.contains("*");
    }

    public boolean isFullyLinked() {
        return userId != null && tenantId != null && actorId != null;
    }
}
