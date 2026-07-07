# Purpose
Redis usage map — who uses it, for what data structure, and why.

# Summary
`tnt-realtime-core` is the dominant Redis consumer (4 adapters: presence, geofence, sessions, pub/sub broadcast). `tnt-roles-core`'s permission cache is **Caffeine-only (in-process L1), no Redis L2 tier** — don't assume otherwise. All access via `ReactiveStringRedisTemplate` (reactive, JSON-serialized values).

# Details

## Module → adapter → data structure

| Module | Adapter | Path | Data structure | Purpose |
|---|---|---|---|---|
| tnt-realtime-core | `RedisPresenceRepository` | `adapter/out/redis/RedisPresenceRepository.java` | Hash | Live actor presence (online/offline, last heartbeat) |
| tnt-realtime-core | `RedisGeofenceZoneRepository` | `adapter/out/redis/RedisGeofenceZoneRepository.java` | Geospatial sorted set | Geofence zone geometry, fast point-in-polygon checks |
| tnt-realtime-core | `RedisSessionRepository` | `adapter/out/redis/RedisSessionRepository.java` | Hash + TTL | WebSocket/SSE session state, ephemeral |
| tnt-realtime-core | `RedisBackedWebSocketBroadcaster` | `adapter/out/websocket/RedisBackedWebSocketBroadcaster.java` | Pub/Sub (pattern: `tnt:rt:topic:*`) | Fan-out real-time updates to all connected clients across app instances |
| tnt-roles-core | `PermissionCache` | `adapter/out/permission/PermissionCache.java` | **Caffeine, in-process — not Redis** | L1 cache for resolved permission sets, TTL from `tnt.roles.permission-cache-ttl-seconds` |
| tnt-route-core | `RoadNetworkProviderAdapter` | `adapter/out/persistence/RoadNetworkProviderAdapter.java` | String (JSON blob) | Caches the immutable `RoadNetwork` aggregate (~5 min TTL) for the VRP solver, avoids re-querying PostGIS per solve |
| tnt-billing-wallet | `RedisIdempotencyStore` | `adapter/out/redis/RedisIdempotencyStore.java` | Hash + TTL | Payment idempotency keys — prevents double-crediting on webhook retries |
| tnt-resource-core | Vehicle location adapter | `adapter/out/cache/` | Geospatial sorted set | Fleet location index for nearest-vehicle lookups |
| yow-event-kernel | `RedisEventIdempotencyStore` | (kernel-level) | Hash + TTL | Cross-module event deduplication |

## Connection config (`application.yml`)
```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
  lettuce.pool: { max-active: 20, max-idle: 10, min-idle: 2 }
```

## Why Caffeine, not Redis, for the permission cache
Permission resolution is on the hot path of every `@RequirePermission`-guarded call. In-process Caffeine avoids a network round-trip; the tradeoff (cache not shared across app instances) is acceptable today because there's only ever one `tnt-bootstrap` instance in dev/staging, and the Kafka-driven invalidation (`PermissionCacheInvalidationListener`) is designed to work correctly even with per-instance caches once the app is horizontally scaled (each instance independently invalidates on the same event). See `security/permissions.md`.

# Links
- `infrastructure/docker.md` — Redis container config
- `security/permissions.md` — PermissionCache architecture
- `infrastructure/kafka.md` — cache invalidation topic

---
> **Comment maintenir ce document** : une nouvelle classe utilisant `ReactiveRedisTemplate`/`RedisTemplate` = une nouvelle ligne. Si `PermissionCache` gagne un jour un tier Redis L2, mettre à jour cette ligne ET `security/permissions.md`.
