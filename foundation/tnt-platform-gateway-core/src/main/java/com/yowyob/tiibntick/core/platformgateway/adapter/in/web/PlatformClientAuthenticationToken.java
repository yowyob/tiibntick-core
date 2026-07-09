package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The reactive-security {@link org.springframework.security.core.Authentication} for a
 * platform client, built by {@code PlatformApiKeyWebFilter} once
 * {@code X-Client-Id}/{@code X-Api-Key} validate successfully.
 *
 * <p>Replaces the earlier design's raw exchange-attribute stash (invisible to any
 * Spring Security authorization primitive) — the client's granted scopes become
 * {@link GrantedAuthority} strings (the raw {@code resource:action} scope value, e.g.
 * {@code "AUTH:*"}), so this token can flow through the reactive {@code SecurityContext}.
 *
 * <p><b>Important:</b> these authorities are NOT evaluated with Spring's native
 * {@code hasAuthority()} (which is exact-string-match only and would silently fail to
 * honor a {@code resource:*} or {@code *} wildcard grant) — both
 * {@code PlatformScopeAuthorizationManager} (coarse, route-level) and
 * {@code PlatformScopeAspect} (fine, per-endpoint via {@code @RequirePlatformScope})
 * read {@link #getScopes()} directly and evaluate it through the shared
 * {@code PermissionMatcher} (see
 * {@code docs/auth/platform-client-management-design.md} §2.4).
 *
 * @author MANFOUO Braun
 */
public class PlatformClientAuthenticationToken extends AbstractAuthenticationToken {

    private final PlatformClientApplication principal;

    public PlatformClientAuthenticationToken(PlatformClientApplication principal) {
        super(toAuthorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> toAuthorities(PlatformClientApplication principal) {
        return principal.scopes().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }

    /** The raw granted scope strings (e.g. {@code "AUTH:*"}) — use with {@code PermissionMatcher}, not {@code hasAuthority()}. */
    public java.util.Set<String> getScopes() {
        return principal.scopes();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public PlatformClientApplication getPrincipal() {
        return principal;
    }
}
