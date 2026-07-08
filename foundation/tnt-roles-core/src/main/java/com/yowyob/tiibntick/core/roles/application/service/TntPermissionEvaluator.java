package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.CheckPermissionUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core application service implementing the TiiBnTick RBAC DSL.
 *
 * <p>Implements {@link CheckPermissionUseCase} by delegating to the Kernel's
 * {@link ReactivePermissionResolver} (which uses the Kernel's {@code RolesPermissionResolver}
 * backed by {@code RoleRepository + UserRoleAssignmentRepository + ReactivePermissionCache}).
 *
 * <p>Adds TiiBnTick-specific permission matching semantics on top of the raw
 * permission strings returned by the Kernel resolver:
 * <ul>
 *   <li>Exact match: {@code "mission:create"}</li>
 *   <li>Resource wildcard: {@code "mission:*"} grants any action on mission</li>
 *   <li>Global wildcard: {@code "*"} grants everything (TNT_ADMIN)</li>
 *   <li>Scoped exact: {@code "mission:create#AGENCY:<agencyId>"}</li>
 *   <li>Scoped wildcard: {@code "mission:*#AGENCY:<agencyId>"}</li>
 * </ul>
 *
 * <p>Also provides direct resolution from the current Spring Security reactive context,
 * reading the JWT-derived authorities (set by {@code TntSecurityConfig.tntJwtAuthenticationConverter})
 * without an additional DB call when the JWT already carries the permissions (hot path).
 *
 * @author MANFOUO Braun
 */
public class TntPermissionEvaluator implements CheckPermissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(TntPermissionEvaluator.class);

    private final ReactivePermissionResolver kernelPermissionResolver;
    private final TntRoleDefinitionRegistry registry;

    public TntPermissionEvaluator(
            ReactivePermissionResolver kernelPermissionResolver,
            TntRoleDefinitionRegistry registry) {
        this.kernelPermissionResolver = kernelPermissionResolver;
        this.registry = registry;
    }

    @Override
    public Mono<Boolean> can(TntPermissionContext ctx, String resource, String action) {
        return resolvePermissions(ctx)
                .map(permissions -> matches(permissions, resource, action, ctx.agencyId()));
    }

    @Override
    public Mono<Boolean> cannot(TntPermissionContext ctx, String resource, String action) {
        return can(ctx, resource, action).map(result -> !result);
    }

    @Override
    public Mono<Void> assertCan(TntPermissionContext ctx, String resource, String action) {
        return can(ctx, resource, action)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.debug("Permission denied: userId={} resource={} action={}", ctx.userId(), resource, action);
                        return Mono.error(TntRoleException.forbidden(resource, action));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Set<String>> resolvePermissions(TntPermissionContext ctx) {
        return tryResolveFromCurrentAuthentication()
                .switchIfEmpty(Mono.defer(
                        () -> kernelPermissionResolver.resolvePermissions(ctx.tenantId(), ctx.userId())
                ));
    }

    @Override
    public Mono<Boolean> hasRole(TntPermissionContext ctx, String roleCode) {
        if (!registry.isKnownRole(roleCode)) {
            return Mono.just(false);
        }
        return kernelPermissionResolver.resolvePermissions(ctx.tenantId(), ctx.userId())
                .map(permissions -> permissions.stream()
                        .anyMatch(p -> p.equals("ROLE_" + roleCode) || p.startsWith("ROLE_" + roleCode + "#")));
    }

    /**
     * Reactive convenience — resolves the current user's permissions from the HTTP security context.
     * Used by the AOP aspect and by services that need permission checks mid-chain.
     *
     * <p>Extracts directly from the JWT-derived authorities set by
     * {@code TntSecurityConfig.tntJwtAuthenticationConverter} (fast path — no DB call), then
     * expands any {@code ROLE_*} authorities to their constituent TiiBnTick permissions via
     * {@link TntRoleDefinitionRegistry}. This allows a user carrying {@code ROLE_TNT_ADMIN}
     * (synthesized from a Kernel {@code ROLE_OWNER} JWT) to pass permission checks without needing
     * explicit permission strings in the JWT.
     */
    public Mono<Boolean> canFromCurrentContext(String resource, String action) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(auth -> {
                    Set<String> rawAuthorities = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toUnmodifiableSet());
                    Set<String> expanded = expandWithRolePermissions(rawAuthorities);
                    UUID agencyId = extractSyntheticUuid(rawAuthorities, "AGENCY_");
                    return Mono.just(matches(expanded, resource, action, agencyId));
                })
                .defaultIfEmpty(false);
    }

    private static UUID extractSyntheticUuid(Set<String> authorities, String prefix) {
        return authorities.stream()
                .filter(a -> a.startsWith(prefix))
                .findFirst()
                .map(a -> {
                    try {
                        return UUID.fromString(a.substring(prefix.length()));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Expands a set of Spring Security authorities by adding the TiiBnTick permissions
     * carried by any {@code ROLE_<code>} authority found in the set.
     * Unknown role codes (e.g. Kernel-only roles like {@code ROLE_OWNER}) are silently ignored.
     */
    private Set<String> expandWithRolePermissions(Set<String> authorities) {
        Set<String> expanded = new HashSet<>(authorities);
        for (String authority : authorities) {
            if (!authority.startsWith("ROLE_")) continue;
            // Strip scope suffix (e.g. ROLE_OWNER#TENANT → OWNER)
            String code = authority.substring(5); // remove "ROLE_"
            int hashIdx = code.indexOf('#');
            if (hashIdx >= 0) code = code.substring(0, hashIdx);
            registry.findByCode(code).ifPresent(def ->
                    expanded.addAll(def.defaultPermissions()));
        }
        return expanded;
    }

    /**
     * Reactive assertion using the current HTTP security context.
     * Preferred in controllers and AOP aspects.
     */
    public Mono<Void> assertCanFromCurrentContext(String resource, String action) {
        return canFromCurrentContext(resource, action)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(TntRoleException.forbidden(resource, action));
                    }
                    return Mono.<Void>empty();
                });
    }

    /**
     * Attempts to resolve permissions from the current Spring Security reactive context.
     * Returns empty if no authenticated context is present (callers should then fall back to DB).
     */
    private Mono<Set<String>> tryResolveFromCurrentAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toUnmodifiableSet()))
                .filter(permissions -> !permissions.isEmpty());
    }

    /**
     * Core permission matching logic with TiiBnTick scope-suffix semantics.
     */
    private boolean matches(Set<String> permissions, String resource, String action, UUID agencyId) {
        String exact = resource + ":" + action;
        String wildcardAction = resource + ":*";

        for (String perm : permissions) {
            if (perm.equals("*")) {
                return true;
            }
            String base = perm.contains("#") ? perm.substring(0, perm.indexOf('#')) : perm;
            if (base.equals(exact) || base.equals(wildcardAction)) {
                if (!perm.contains("#")) {
                    return true;
                }
                if (agencyId != null && perm.contains("#AGENCY:" + agencyId)) {
                    return true;
                }
                if (perm.contains("#SYSTEM") || perm.contains("#TENANT")) {
                    return true;
                }
            }
        }
        return false;
    }
}
