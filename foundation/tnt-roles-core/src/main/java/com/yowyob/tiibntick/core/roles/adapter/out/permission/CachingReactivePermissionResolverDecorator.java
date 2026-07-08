package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * "Niveau 1" — wraps whichever {@code ReactivePermissionResolver} delegate
 * {@code tnt.roles.permission.mode} selected (LOCAL/REMOTE/HYBRID) with an in-process
 * {@link PermissionCache}, independent of the resolution strategy underneath.
 *
 * <p>{@link com.yowyob.tiibntick.core.roles.adapter.in.kafka.PermissionCacheInvalidationListener}
 * holds a reference to the same {@link PermissionCache} instance to evict entries when a
 * role/permission-change event arrives, so callers never see stale data longer than one
 * Kafka round-trip.
 *
 * @author MANFOUO Braun
 */
public class CachingReactivePermissionResolverDecorator implements ReactivePermissionResolver {

    private final ReactivePermissionResolver delegate;
    private final PermissionCache cache;

    public CachingReactivePermissionResolverDecorator(ReactivePermissionResolver delegate, PermissionCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Mono<Set<String>> resolvePermissions(UUID tenantId, UUID userId) {
        Set<String> cached = cache.getIfPresent(tenantId, userId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return delegate.resolvePermissions(tenantId, userId)
                .doOnNext(permissions -> cache.put(tenantId, userId, permissions));
    }

    public PermissionCache cache() {
        return cache;
    }
}
