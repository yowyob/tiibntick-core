package com.yowyob.tiibntick.core.roles.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-instance cache of "is this account a platform administrator" (decision D-168): 60 s by default, evicted on
 * every instance by {@code tnt.roles.permission-changed} as soon as a grant or a revocation is committed, so a
 * revocation takes effect within seconds, and within the TTL at worst when the broadcast is lost.
 *
 * @author MANFOUO Braun
 */
public class PlatformAdminCache {

    private final Cache<UUID, Boolean> admins;
    /** Owners whose protected grant was confirmed recently: spares a registry read per request. */
    private final Cache<UUID, Boolean> confirmedOwners;

    public PlatformAdminCache(int ttlSeconds) {
        Duration ttl = Duration.ofSeconds(Math.max(ttlSeconds, 1));
        this.admins = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(50_000).build();
        this.confirmedOwners = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(100).build();
    }

    public Boolean getIfPresent(UUID userId) {
        return admins.getIfPresent(userId);
    }

    public void put(UUID userId, boolean admin) {
        admins.put(userId, admin);
    }

    public boolean ownerConfirmed(UUID userId) {
        return Boolean.TRUE.equals(confirmedOwners.getIfPresent(userId));
    }

    public void confirmOwner(UUID userId) {
        confirmedOwners.put(userId, Boolean.TRUE);
        admins.put(userId, Boolean.TRUE);
    }

    public void invalidate(UUID userId) {
        admins.invalidate(userId);
        confirmedOwners.invalidate(userId);
    }

    public void invalidateAll() {
        admins.invalidateAll();
        confirmedOwners.invalidateAll();
    }
}
