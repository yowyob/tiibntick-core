package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * In-process L1 cache for resolved permission sets, keyed by {@code tenantId:userId}.
 *
 * <p>Sits in front of whichever {@code ReactivePermissionResolver} delegate is active
 * (LOCAL/REMOTE/HYBRID) via {@link CachingReactivePermissionResolverDecorator}. Entries
 * are invalidated either by TTL expiry or explicitly by
 * {@link com.yowyob.tiibntick.core.roles.adapter.in.kafka.PermissionCacheInvalidationListener}
 * when a role/permission-change event arrives.
 *
 * @author MANFOUO Braun
 */
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

    private final Cache<String, Set<String>> cache;

    public PermissionCache(int ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(ttlSeconds, 1)))
                .maximumSize(50_000)
                .build();
    }

    public Set<String> getIfPresent(UUID tenantId, UUID userId) {
        return cache.getIfPresent(key(tenantId, userId));
    }

    public void put(UUID tenantId, UUID userId, Set<String> permissions) {
        cache.put(key(tenantId, userId), permissions);
    }

    public void invalidate(UUID tenantId, UUID userId) {
        cache.invalidate(key(tenantId, userId));
    }

    public void invalidateTenant(UUID tenantId) {
        String prefix = tenantId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void invalidateAll() {
        log.info("Invalidating entire local permission cache ({} entries)", cache.estimatedSize());
        cache.invalidateAll();
    }

    private static String key(UUID tenantId, UUID userId) {
        return tenantId + ":" + userId;
    }
}
