package com.yowyob.tiibntick.core.auth.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * TiiBnTick-enriched security context extracted from the Kernel token (ApiKeyAuthenticationToken).
 * This is a pure domain model — no Spring Security dependency, no logic beyond invariants.
 *
 * <p>Wraps what the Kernel JWT already carries (userId, tenantId, actorId, agencyId, permissions)
 * and adds TiiBnTick-specific metadata (freelancer flag, clientApplicationId).
 *
 * <p>The actorId links back to tnt-actor-core's DelivererProfile / FreelancerProfile
 * without tnt-auth-core depending on tnt-actor-core (inversion via IYowAuthTntAdapter).
 *
 * @author MANFOUO Braun
 */
public record TntSecurityContext(
        UUID userId,
        UUID tenantId,
        UUID actorId,
        UUID organizationId,
        UUID agencyId,
        String email,
        Set<String> roles,
        Set<String> permissions,
        boolean authenticated,
        boolean freelancer,
        String clientApplicationId
) {

    private static final String PERMISSION_SEPARATOR = ":";
    private static final String WILDCARD = "*";

    public TntSecurityContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * Returns true if this context carries a specific permission.
     * Permission format: {@code resource:action} or {@code resource:action:scope}.
     * A wildcard entry {@code resource:*} grants any action on that resource.
     */
    public boolean hasPermission(String resource, String action) {
        String exact = resource + PERMISSION_SEPARATOR + action;
        String wildcardAction = resource + PERMISSION_SEPARATOR + WILDCARD;
        return permissions.contains(exact)
                || permissions.contains(wildcardAction)
                || permissions.contains(WILDCARD);
    }

    /**
     * Returns true if this context carries the given role (e.g. ROLE_AGENCY_MANAGER).
     */
    public boolean hasRole(String role) {
        String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.contains(role) || roles.contains(normalized);
    }

    /**
     * Returns true when both userId and tenantId are present and authenticated flag is set.
     */
    public boolean isFullyAuthenticated() {
        return authenticated && userId != null && tenantId != null;
    }

    /**
     * Returns true when the actorId is known, meaning the user is fully linked to a TiiBnTick actor.
     */
    public boolean hasActorProfile() {
        return actorId != null;
    }

    /**
     * Factory — anonymous (unauthenticated) context for public endpoints.
     */
    public static TntSecurityContext anonymous() {
        return new TntSecurityContext(
                null, null, null, null, null,
                null, Set.of(), Set.of(), false, false, null
        );
    }

    /**
     * Builder for constructing a TntSecurityContext from resolved fields.
     * Used by TntSecurityContextService when wrapping ApiKeyAuthenticationToken.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID userId;
        private UUID tenantId;
        private UUID actorId;
        private UUID organizationId;
        private UUID agencyId;
        private String email;
        private Set<String> roles = Set.of();
        private Set<String> permissions = Set.of();
        private boolean authenticated;
        private boolean freelancer;
        private String clientApplicationId;

        private Builder() {}

        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder actorId(UUID actorId) { this.actorId = actorId; return this; }
        public Builder organizationId(UUID organizationId) { this.organizationId = organizationId; return this; }
        public Builder agencyId(UUID agencyId) { this.agencyId = agencyId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder roles(Set<String> roles) { this.roles = roles; return this; }
        public Builder permissions(Set<String> permissions) { this.permissions = permissions; return this; }
        public Builder authenticated(boolean authenticated) { this.authenticated = authenticated; return this; }
        public Builder freelancer(boolean freelancer) { this.freelancer = freelancer; return this; }
        public Builder clientApplicationId(String clientApplicationId) { this.clientApplicationId = clientApplicationId; return this; }

        public TntSecurityContext build() {
            return new TntSecurityContext(
                    userId, tenantId, actorId, organizationId, agencyId,
                    email, roles, permissions, authenticated, freelancer, clientApplicationId
            );
        }
    }
}
