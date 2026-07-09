package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * The resolved platform-client principal for the current request — attached to the
 * reactive {@code SecurityContext} via {@code PlatformClientAuthenticationToken} once
 * {@code PlatformApiKeyWebFilter} successfully validates {@code X-Client-Id}/{@code X-Api-Key}.
 *
 * <p>Distinct from the persistence-level {@link PlatformClient} aggregate: this is the
 * lightweight, request-scoped view — just enough to authorize (scopes) and audit
 * (clientId) the current call, resolved once per request (behind a short-TTL cache, see
 * {@code docs/auth/platform-client-management-design.md} §7) rather than re-fetched from
 * every column of the full aggregate.
 *
 * @author MANFOUO Braun
 */
public record PlatformClientApplication(
        UUID id,
        String clientId,
        String platformCode,
        Environment environment,
        Set<String> scopes
) {
}
