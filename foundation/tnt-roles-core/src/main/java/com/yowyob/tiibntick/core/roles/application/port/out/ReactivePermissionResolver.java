package com.yowyob.tiibntick.core.roles.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the effective permission strings granted to a user within a tenant.
 *
 * <p>Local replacement for the Kernel's {@code ReactivePermissionResolver} port —
 * TiiBnTick no longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}:
 * Kernel is HTTP-only). Strategy is selected by {@code tnt.roles.permission.mode}
 * (LOCAL/REMOTE/HYBRID) — see {@code TntRolesAutoConfiguration}.
 *
 * @author MANFOUO Braun
 */
public interface ReactivePermissionResolver {

    Mono<Set<String>> resolvePermissions(UUID tenantId, UUID userId);
}
