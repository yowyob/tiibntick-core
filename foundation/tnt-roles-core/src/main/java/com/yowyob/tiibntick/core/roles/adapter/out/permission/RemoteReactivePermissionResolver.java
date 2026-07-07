package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.application.port.out.ReactivePermissionResolver;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves a user's effective permissions by calling the Kernel over HTTP.
 *
 * <p>Architecture rule: never inject Kernel Spring beans directly — this adapter only
 * uses the shared {@code kernelWebClient}, matching {@code KernelRoleProvisioningAdapter}.
 *
 * <p>The Kernel does not expose a permission-resolution REST endpoint yet (confirmed
 * against its live OpenAPI spec). This adapter calls the documented contract it's
 * expected to land on; until then every call degrades gracefully to an empty set with
 * a logged warning rather than failing the request. No code change is needed on either
 * side once the endpoint ships — just flip {@code tnt.roles.permission.mode} to
 * {@code REMOTE} or {@code HYBRID}.
 *
 * @author MANFOUO Braun
 */
public class RemoteReactivePermissionResolver implements ReactivePermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(RemoteReactivePermissionResolver.class);

    private final WebClient kernelWebClient;

    public RemoteReactivePermissionResolver(WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<Set<String>> resolvePermissions(UUID tenantId, UUID userId) {
        // The Kernel does not yet expose a permission-resolution REST endpoint
        // (confirmed against /v3/api-docs — no /api/permissions/resolve or equivalent).
        // When the endpoint ships, replace the URI below and remove this comment.
        // Until then this resolver degrades gracefully to an empty set; the LOCAL
        // strategy (default) is authoritative — see TntRolesAutoConfiguration.
        log.warn("Kernel permission resolution unavailable for user {} (tenant {}) — no endpoint yet, returning empty set.",
                userId, tenantId);
        return Mono.just(Set.of());
    }
}
