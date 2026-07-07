package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.application.port.out.ReactivePermissionResolver;

import java.util.Set;
import java.util.UUID;

/**
 * Tries {@link LocalReactivePermissionResolver} first; if it yields no permissions
 * (e.g. nothing has been provisioned locally for this user yet), falls back to
 * {@link RemoteReactivePermissionResolver}.
 *
 * @author MANFOUO Braun
 */
public class HybridReactivePermissionResolver implements ReactivePermissionResolver {

    private final LocalReactivePermissionResolver local;
    private final RemoteReactivePermissionResolver remote;

    public HybridReactivePermissionResolver(LocalReactivePermissionResolver local, RemoteReactivePermissionResolver remote) {
        this.local = local;
        this.remote = remote;
    }

    @Override
    public Mono<Set<String>> resolvePermissions(UUID tenantId, UUID userId) {
        return local.resolvePermissions(tenantId, userId)
                .filter(permissions -> !permissions.isEmpty())
                .switchIfEmpty(remote.resolvePermissions(tenantId, userId));
    }
}
