package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative scope enforcement for platform-gateway endpoints — the machine-to-machine
 * mirror of {@code tnt-roles-core}'s {@code @RequirePermission}, but checked against the
 * current {@link PlatformClientAuthenticationToken}'s granted scopes instead of a JWT's
 * user permissions (see {@code docs/auth/platform-client-management-design.md} §2.4).
 *
 * <p>Applied to methods returning {@code Mono<?>} or {@code Flux<?>}; {@link PlatformScopeAspect}
 * prepends a check via the shared {@code PermissionMatcher} against
 * {@link PlatformClientAuthenticationToken#getScopes()}. On failure, the reactive chain
 * emits {@code TntPlatformGatewayException.scopeForbidden(resource, action)}.
 *
 * <p>Usage:
 * <pre>{@code
 * @RequirePlatformScope(resource = "delivery", action = "read")
 * public Mono<TrackingInfo> track(String code) { ... }
 * }</pre>
 *
 * @author MANFOUO Braun
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePlatformScope {

    /** Scope resource — a gateway block ({@code auth}, {@code sso}, {@code onboarding}) or curated business module. */
    String resource();

    /** Scope action (e.g. {@code read}, {@code write}, or {@code *}). */
    String action();
}
