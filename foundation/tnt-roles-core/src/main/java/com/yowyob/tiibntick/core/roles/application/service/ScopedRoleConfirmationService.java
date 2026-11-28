package com.yowyob.tiibntick.core.roles.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.tiibntick.core.roles.application.port.in.ConfirmScopedRolesUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IScopedRoleAssignmentsPort;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decision D-176. Reads the Core's assignments through a short cache (same TTL as the platform-admin cache, D-168),
 * emptied for the user on every {@code tnt.roles.permission-changed} event, so an assignment or a revocation is seen
 * at once on every instance.
 */
public class ScopedRoleConfirmationService implements ConfirmScopedRolesUseCase {

    private record Key(UUID tenantId, UUID userId) {
    }

    private final IScopedRoleAssignmentsPort assignments;
    private final Cache<Key, Set<String>> cache;

    public ScopedRoleConfirmationService(IScopedRoleAssignmentsPort assignments, int ttlSeconds) {
        this.assignments = Objects.requireNonNull(assignments);
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(Math.max(ttlSeconds, 1)))
                .maximumSize(50_000).build();
    }

    @Override
    public Mono<Set<String>> confirmedScopedRoles(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return Mono.just(Set.of());
        }
        Key key = new Key(tenantId, userId);
        Set<String> cached = cache.getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }
        return assignments.scopedRoleEntries(tenantId, userId).collect(java.util.stream.Collectors.toUnmodifiableSet())
                .doOnNext(found -> cache.put(key, found));
    }

    public void invalidate(UUID tenantId, UUID userId) {
        if (tenantId != null && userId != null) {
            cache.invalidate(new Key(tenantId, userId));
        } else {
            invalidateAll();
        }
    }

    public void invalidateTenant(UUID tenantId) {
        cache.asMap().keySet().removeIf(k -> k.tenantId().equals(tenantId));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
