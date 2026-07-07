package com.yowyob.tiibntick.core.route.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRoadNetworkUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import com.yowyob.tiibntick.core.route.application.port.out.IRoadNetworkProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbound adapter implementing {@link IRoadNetworkProvider} with a two-tier
 * caching strategy for optimal A* pathfinding performance:
 *
 * <ul>
 *   <li><b>L1 (local)</b> — {@link ConcurrentHashMap} with 30 s TTL.
 *       Provides sub-microsecond reads within the same JVM. Automatically
 *       evicts expired entries to bound memory usage.</li>
 *   <li><b>L2 (Redis)</b> — ReactiveStringRedisTemplate with 5 min TTL.
 *       Distributed cache shared across all tnt-bootstrap instances.
 *       Serializes nodes and arcs as JSON; reconstructs the immutable
 *       {@link RoadNetwork} (including adjacency list) via
 *       {@link RoadNetwork#build(UUID, Collection, Collection)}.</li>
 * </ul>
 *
 * <p><b>Graceful degradation:</b> if Redis is unreachable the adapter
 * transparently falls back to L1 → DB, ensuring the routing engine
 * remains operational.</p>
 *
 * <p><b>Hexagonal position:</b> outbound adapter in tnt-route-core,
 * implementing the {@link IRoadNetworkProvider} port consumed by
 * {@code AStarPathfinderService} and {@code TourPlannerService}.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class RoadNetworkProviderAdapter implements IRoadNetworkProvider {

    private static final Logger log = LoggerFactory.getLogger(RoadNetworkProviderAdapter.class);

    /** Redis key prefix: {@code tnt:route:network:{tenantId}}. */
    private static final String CACHE_KEY_PREFIX = "tnt:route:network:";

    /** L2 distributed cache TTL — aligned with RoadNetwork design (5 min). */
    private static final Duration REDIS_TTL = Duration.ofMinutes(5);

    /** L1 local cache TTL — short to keep traffic-factor updates reasonably fresh. */
    private static final long LOCAL_TTL_MS = Duration.ofSeconds(30).toMillis();

    // ── L1 local cache ──────────────────────────────────────────────────────
    private final ConcurrentHashMap<UUID, CachedEntry> localCache = new ConcurrentHashMap<>();

    // ── Dependencies ────────────────────────────────────────────────────────
    private final IManageRoadNetworkUseCase roadNetworkUseCase;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RoadNetworkProviderAdapter(
            IManageRoadNetworkUseCase roadNetworkUseCase,
            @Qualifier("routeRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("routeObjectMapper") ObjectMapper objectMapper) {
        this.roadNetworkUseCase = Objects.requireNonNull(roadNetworkUseCase);
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  IRoadNetworkProvider
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Mono<RoadNetwork> loadNetwork(UUID tenantId) {
        return Mono.defer(() -> {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            evictExpiredLocalEntries();
            return loadFromL1(tenantId)
                    .switchIfEmpty(Mono.defer(() -> loadFromL2(tenantId)))
                    .switchIfEmpty(Mono.defer(() -> loadFromDatabase(tenantId)));
        });
    }

    /**
     * Evicts the cached network for the given tenant from both L1 and L2.
     * Can be called after traffic-factor updates or arc mutations.
     */
    public Mono<Void> evict(UUID tenantId) {
        localCache.remove(tenantId);
        return redisTemplate.delete(cacheKey(tenantId))
                .doOnSuccess(n -> log.debug("Evicted road-network cache for tenant {}", tenantId))
                .doOnError(ex -> log.warn("Redis evict failed for tenant {}: {}", tenantId, ex.getMessage()))
                .onErrorComplete()
                .then();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cache tier reads
    // ═══════════════════════════════════════════════════════════════════════════

    private Mono<RoadNetwork> loadFromL1(UUID tenantId) {
        CachedEntry entry = localCache.get(tenantId);
        if (entry != null && !entry.isExpired()) {
            log.trace("L1 cache HIT for tenant {}", tenantId);
            return Mono.just(entry.network);
        }
        return Mono.empty();
    }

    private Mono<RoadNetwork> loadFromL2(UUID tenantId) {
        return redisTemplate.opsForValue()
                .get(cacheKey(tenantId))
                .flatMap(this::deserializeNetwork)
                .doOnSuccess(network -> {
                    if (network != null) {
                        localCache.put(tenantId, new CachedEntry(network));
                        log.debug("L2 cache HIT for tenant {} ({} nodes, {} arcs)",
                                tenantId, network.nodeCount(), network.arcCount());
                    }
                })
                .doOnError(ex -> log.warn("Redis read failed for tenant {}: {}", tenantId, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty());
    }

    private Mono<RoadNetwork> loadFromDatabase(UUID tenantId) {
        log.debug("Cache MISS for tenant {} — loading from database", tenantId);
        return roadNetworkUseCase.loadNetwork(tenantId)
                .flatMap(network -> populateCaches(tenantId, network).thenReturn(network));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cache writes
    // ═══════════════════════════════════════════════════════════════════════════

    private Mono<RoadNetwork> populateCaches(UUID tenantId, RoadNetwork network) {
        // Always populate L1
        localCache.put(tenantId, new CachedEntry(network));

        // Populate L2 (Redis) — best-effort, never fails the caller
        return serializeNetwork(network)
                .flatMap(json -> redisTemplate.opsForValue()
                        .set(cacheKey(tenantId), json, REDIS_TTL))
                .doOnSuccess(ok -> log.info("Cached road-network for tenant {} in Redis (TTL={}s, {} nodes, {} arcs)",
                        tenantId, REDIS_TTL.toSeconds(), network.nodeCount(), network.arcCount()))
                .doOnError(ex -> log.warn("Redis write failed for tenant {}: {}", tenantId, ex.getMessage()))
                .onErrorComplete()
                .thenReturn(network);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Serialization — compact DTO records for Redis JSON storage
    // ═══════════════════════════════════════════════════════════════════════════

    /** Compact DTO for {@link RoadNode} serialization. */
    record NodeDto(String id, String tenantId, String type, double lat, double lng,
                   String name, String cityCode, boolean active, Integer capacitySlots,
                   String createdAt, String updatedAt) {}

    /** Compact DTO for {@link RoadArc} serialization. */
    record ArcDto(String id, String tenantId, String sourceId, String targetId,
                  double distanceKm, String roadType, double baseSpeedKmh,
                  double trafficFactor, boolean bidirectional,
                  String createdAt, String updatedAt) {}

    /** Wrapper DTO holding the full network state for a single tenant. */
    record NetworkDto(String tenantId, List<NodeDto> nodes, List<ArcDto> arcs) {}

    private Mono<String> serializeNetwork(RoadNetwork network) {
        try {
            List<NodeDto> nodes = new ArrayList<>(network.nodeCount());
            for (RoadNode n : network.nodes().values()) {
                nodes.add(new NodeDto(
                        n.id().value(), n.tenantId().toString(), n.type().name(),
                        n.coordinates().latitude(), n.coordinates().longitude(),
                        n.name(), n.cityCode(), n.isActive(), n.capacitySlots(),
                        n.createdAt().toString(), n.updatedAt().toString()));
            }
            List<ArcDto> arcs = new ArrayList<>(network.arcCount());
            for (RoadArc a : network.arcs().values()) {
                arcs.add(new ArcDto(
                        a.id().value(), a.tenantId().toString(),
                        a.sourceId().value(), a.targetId().value(),
                        a.distanceKm(), a.roadType().name(), a.baseSpeedKmh(),
                        a.trafficFactor(), a.isBidirectional(),
                        a.createdAt().toString(), a.updatedAt().toString()));
            }
            NetworkDto dto = new NetworkDto(network.tenantId().toString(), nodes, arcs);
            return Mono.just(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize RoadNetwork for tenant {}: {}",
                    network.tenantId(), ex.getMessage());
            return Mono.error(ex);
        }
    }

    private Mono<RoadNetwork> deserializeNetwork(String json) {
        try {
            NetworkDto dto = objectMapper.readValue(json, NetworkDto.class);
            UUID tenantId = UUID.fromString(dto.tenantId());

            List<RoadNode> nodes = new ArrayList<>(dto.nodes.size());
            for (NodeDto n : dto.nodes) {
                nodes.add(RoadNode.rehydrate(
                        RoadNodeId.of(n.id), UUID.fromString(n.tenantId),
                        NodeType.valueOf(n.type), GeoPoint.of(n.lat, n.lng),
                        n.name, n.cityCode, n.active, n.capacitySlots,
                        Instant.parse(n.createdAt), Instant.parse(n.updatedAt)));
            }

            List<RoadArc> arcs = new ArrayList<>(dto.arcs.size());
            for (ArcDto a : dto.arcs) {
                arcs.add(RoadArc.rehydrate(
                        RoadArcId.of(a.id), UUID.fromString(a.tenantId),
                        RoadNodeId.of(a.sourceId), RoadNodeId.of(a.targetId),
                        a.distanceKm, RoadType.valueOf(a.roadType), a.baseSpeedKmh(),
                        a.trafficFactor(), a.bidirectional,
                        Instant.parse(a.createdAt), Instant.parse(a.updatedAt)));
            }

            RoadNetwork network = RoadNetwork.build(tenantId, nodes, arcs);
            log.debug("Deserialized RoadNetwork from Redis: {} nodes, {} arcs",
                    network.nodeCount(), network.arcCount());
            return Mono.just(network);
        } catch (Exception ex) {
            log.error("Failed to deserialize RoadNetwork from Redis JSON: {}", ex.getMessage());
            return Mono.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  L1 maintenance
    // ═══════════════════════════════════════════════════════════════════════════

    /** Removes expired entries from the local cache to bound memory. */
    private void evictExpiredLocalEntries() {
        localCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private String cacheKey(UUID tenantId) {
        return CACHE_KEY_PREFIX + tenantId;
    }

    /** Wrapper holding a {@link RoadNetwork} and its insertion timestamp for L1 TTL. */
    private record CachedEntry(RoadNetwork network, long cachedAtMs) {
        CachedEntry(RoadNetwork network) {
            this(network, System.currentTimeMillis());
        }
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMs > LOCAL_TTL_MS;
        }
    }
}
