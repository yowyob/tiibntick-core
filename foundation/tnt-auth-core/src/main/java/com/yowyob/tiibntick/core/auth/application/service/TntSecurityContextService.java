package com.yowyob.tiibntick.core.auth.application.service;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.application.port.in.ValidateTokenUseCase;
import com.yowyob.tiibntick.core.auth.application.port.out.IYowAuthTntAdapter;
import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenClaims;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.config.ApiKeyAuthenticationToken;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core application service for tnt-auth-core.
 * Implements both {@link ResolveCurrentUserUseCase} and {@link ValidateTokenUseCase}.
 *
 * <p>Reads the Kernel's {@link ApiKeyAuthenticationToken} from the reactive security
 * context (populated by the Kernel's filter chain) and maps it to {@link TntSecurityContext}.
 * Optionally enriches the context with actor data via {@link IYowAuthTntAdapter} when
 * the adapter implementation is available (injected by tnt-actor-core at runtime).
 *
 * <p>Contains NO authentication logic — all JWT validation is delegated to
 * the Kernel's {@link UserSessionTokenService}.
 *
 * <h3>Authority splitting (v1.1 — tnt-roles-core integration)</h3>
 * <p>Uses {@link TntRole#isKnownRole(String)} to accurately split JWT authorities:
 * <ul>
 *   <li>{@code roles} — authorities that match a known TiiBnTick role code,
 *       stored as {@code ROLE_<CODE>} for Spring Security compatibility</li>
 *   <li>{@code permissions} — raw {@code resource:action} strings that are NOT role codes</li>
 * </ul>
 * This ensures {@code TntSecurityContext.hasRole()} works correctly for all
 * TiiBnTick business roles without relying solely on the {@code ROLE_} prefix convention.
 *
 * <p>REFACTORED (2026-06-15): Uses TntJwtValidator instead of UserSessionTokenService.
 * Removed dependency on Kernel's UserSessionTokenService.
 * @author MANFOUO Braun
 */
public class TntSecurityContextService implements ResolveCurrentUserUseCase, ValidateTokenUseCase {

    private final TntJwtValidator jwtValidator;
    private final IYowAuthTntAdapter yowAuthTntAdapter;

    public TntSecurityContextService(
            TntJwtValidator jwtValidator,
            IYowAuthTntAdapter yowAuthTntAdapter) {
        this.jwtValidator = jwtValidator;
        this.yowAuthTntAdapter = yowAuthTntAdapter;
    }

    @Override
    public Mono<TntSecurityContext> resolveCurrentContext() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(this::buildContext)
                .switchIfEmpty(Mono.error(TntAuthException.missingContext()));
    }

    @Override
    public Mono<TntUserIdentity> resolveCurrentIdentity() {
        return resolveCurrentContext().map(TntUserIdentity::from);
    }

    @Override
    public Mono<TntSecurityContext> resolveCurrentContextOrAnonymous() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(this::buildContext)
                .defaultIfEmpty(TntSecurityContext.anonymous());
    }

    @Override
    public Mono<TntTokenClaims> validateAndExtract(String bearerToken) {
        return jwtValidator.validateAndExtract(bearerToken);
    }

    @Override
    public boolean isValid(String bearerToken) {
        return jwtValidator.isValid(bearerToken);
    }

    private Mono<TntSecurityContext> buildContext(Authentication authentication) {
        if (authentication instanceof ApiKeyAuthenticationToken token) {
            return buildContextFromApiKeyToken(token);
        }
        // Bearer JWT path: tntJwtAuthenticationConverter already extracted
        // ACTOR_<uuid>, TENANT_<uuid>, AGENCY_<uuid>, ORG_<uuid> as synthetic authorities.
        return buildContextFromAuthorities(authentication);
    }

    private Mono<TntSecurityContext> buildContextFromApiKeyToken(ApiKeyAuthenticationToken token) {
        Set<String> allAuthorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> roles = allAuthorities.stream()
                .filter(a -> a.startsWith("ROLE_") || TntRole.isKnownRole(a.replace("ROLE_", "")))
                .map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions = allAuthorities.stream()
                .filter(a -> !a.startsWith("ROLE_") && !TntRole.isKnownRole(a))
                .collect(Collectors.toUnmodifiableSet());

        UUID userId = token.userId();
        UUID tenantId = token.tenantId();
        UUID actorId = token.actorId();
        UUID organizationId = token.organizationId();
        UUID agencyId = token.agencyId();
        String clientAppId = token.clientApplicationId() != null
                ? token.clientApplicationId().toString()
                : null;

        TntSecurityContext.Builder base = TntSecurityContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .actorId(actorId)
                .organizationId(organizationId)
                .agencyId(agencyId)
                .roles(roles)
                .permissions(permissions)
                .authenticated(true)
                .clientApplicationId(clientAppId);

        if (actorId == null && userId != null && tenantId != null) {
            return yowAuthTntAdapter.resolveActorId(userId, tenantId)
                    .flatMap(optActorId -> {
                        UUID resolvedActorId = optActorId.orElse(null);
                        if (resolvedActorId == null) {
                            return Mono.just(base.build());
                        }
                        return enrichWithActorData(base, resolvedActorId, tenantId, agencyId);
                    })
                    .onErrorResume(e -> Mono.just(base.build()));
        }

        if (actorId != null && tenantId != null) {
            return enrichWithActorData(base, actorId, tenantId, agencyId);
        }

        return Mono.just(base.build());
    }

    /**
     * Builds a {@link TntSecurityContext} from a standard Spring Security authentication
     * carrying synthetic authorities set by {@code tntJwtAuthenticationConverter}:
     * {@code ACTOR_<uuid>}, {@code TENANT_<uuid>}, {@code AGENCY_<uuid>}, {@code ORG_<uuid>}.
     * Used for Bearer JWT tokens validated by the OAuth2 resource server filter.
     */
    private Mono<TntSecurityContext> buildContextFromAuthorities(Authentication authentication) {
        Set<String> allAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        // Extract synthetic UUID authorities added by tntJwtAuthenticationConverter
        UUID actorId  = extractSyntheticUuid(allAuthorities, "ACTOR_");
        UUID tenantId = extractSyntheticUuid(allAuthorities, "TENANT_");
        UUID agencyId = extractSyntheticUuid(allAuthorities, "AGENCY_");
        UUID orgId    = extractSyntheticUuid(allAuthorities, "ORG_");

        // sub claim → userId (principal name on JwtAuthenticationToken)
        UUID userId = parseUuidOrNull(authentication.getName());

        Set<String> roles = allAuthorities.stream()
                .filter(a -> a.startsWith("ROLE_") || TntRole.isKnownRole(a.replace("ROLE_", "")))
                .filter(a -> !a.startsWith("ACTOR_") && !a.startsWith("TENANT_")
                          && !a.startsWith("AGENCY_") && !a.startsWith("ORG_"))
                .map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions = allAuthorities.stream()
                .filter(a -> !a.startsWith("ROLE_") && !a.startsWith("ACTOR_")
                          && !a.startsWith("TENANT_") && !a.startsWith("AGENCY_")
                          && !a.startsWith("ORG_") && !TntRole.isKnownRole(a))
                .collect(Collectors.toUnmodifiableSet());

        TntSecurityContext.Builder base = TntSecurityContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .actorId(actorId)
                .organizationId(orgId)
                .agencyId(agencyId)
                .roles(roles)
                .permissions(permissions)
                .authenticated(true);

        if (actorId != null && tenantId != null) {
            return enrichWithActorData(base, actorId, tenantId, agencyId);
        }

        return Mono.just(base.build());
    }

    private static UUID extractSyntheticUuid(Set<String> authorities, String prefix) {
        return authorities.stream()
                .filter(a -> a.startsWith(prefix))
                .findFirst()
                .map(a -> parseUuidOrNull(a.substring(prefix.length())))
                .orElse(null);
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }

    private Mono<TntSecurityContext> enrichWithActorData(
            TntSecurityContext.Builder base,
            UUID actorId,
            UUID tenantId,
            UUID agencyId) {
        return Mono.zip(
                yowAuthTntAdapter.isFreelancer(actorId, tenantId).onErrorReturn(false),
                agencyId != null
                        ? Mono.just(Optional.of(agencyId))
                        : yowAuthTntAdapter.resolveAgencyId(actorId, tenantId).onErrorReturn(Optional.empty())
        ).map(tuple -> base
                .actorId(actorId)
                .freelancer(tuple.getT1())
                .agencyId(tuple.getT2().orElse(agencyId))
                .build());
    }
}
