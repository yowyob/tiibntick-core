package com.yowyob.tiibntick.core.route.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRoadNetworkUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RoadNetworkProviderAdapter}.
 * Verifies the two-tier caching strategy (L1 local, L2 Redis) and
 * graceful degradation when Redis is unavailable.
 */
@ExtendWith(MockitoExtension.class)
class RoadNetworkProviderAdapterTest {

    @Mock private IManageRoadNetworkUseCase roadNetworkUseCase;
    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOps;

    private RoadNetworkProviderAdapter adapter;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CACHE_KEY = "tnt:route:network:" + TENANT;
    private static final ObjectMapper MAPPER = createMapper();

    @BeforeEach
    void setUp() {
        adapter = new RoadNetworkProviderAdapter(roadNetworkUseCase, redisTemplate, MAPPER);
    }

    private static ObjectMapper createMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private RoadNetwork buildSampleNetwork() {
        RoadNode depot = RoadNode.rehydrate(
                RoadNodeId.of("depot-1"), TENANT, NodeType.DEPOT,
                GeoPoint.of(3.848, 11.502), "Depot Central", "YDE", true, 100,
                Instant.now(), Instant.now());
        RoadNode waypoint = RoadNode.rehydrate(
                RoadNodeId.of("wp-1"), TENANT, NodeType.WAYPOINT,
                GeoPoint.of(3.9, 11.6), "Carrefour Mvog-Ada", "YDE", true, null,
                Instant.now(), Instant.now());
        RoadArc arc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT, depot.id(), waypoint.id(),
                5.0, RoadType.PAVED, 50.0, 1.0, true,
                Instant.now(), Instant.now());
        return RoadNetwork.build(TENANT, List.of(depot, waypoint), List.of(arc));
    }

    private void setupRedisMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.empty());
    }

    private void setupRedisHit(String json) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.just(json));
    }

    private void setupRedisWriteSuccess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(eq(CACHE_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
    }

    private void setupRedisDown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(Mono.error(new RuntimeException("Redis connection refused")));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cache-miss → DB → populate caches
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_cacheMiss_loadsFromDbAndPopulatesCaches() {
        RoadNetwork network = buildSampleNetwork();
        setupRedisMiss();
        setupRedisWriteSuccess();
        when(roadNetworkUseCase.loadNetwork(TENANT)).thenReturn(Mono.just(network));

        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> {
                    assertThat(result.tenantId()).isEqualTo(TENANT);
                    assertThat(result.nodeCount()).isEqualTo(2);
                    assertThat(result.arcCount()).isEqualTo(1);
                    assertThat(result.containsNode(RoadNodeId.of("depot-1"))).isTrue();
                })
                .verifyComplete();

        verify(roadNetworkUseCase).loadNetwork(TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  L1 cache hit (second call)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_secondCall_returnsFromL1Cache() {
        RoadNetwork network = buildSampleNetwork();
        setupRedisMiss();
        setupRedisWriteSuccess();
        when(roadNetworkUseCase.loadNetwork(TENANT)).thenReturn(Mono.just(network));

        // First call: populates L1
        adapter.loadNetwork(TENANT).block();

        // Second call: should use L1, no further Redis/DB calls
        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> assertThat(result.nodeCount()).isEqualTo(2))
                .verifyComplete();

        // DB called only once
        verify(roadNetworkUseCase, times(1)).loadNetwork(TENANT);
        // Redis get called only once (for the first call)
        verify(valueOps, times(1)).get(CACHE_KEY);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Redis (L2) cache hit
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_redisHit_deserializesFromL2() throws Exception {
        RoadNetwork network = buildSampleNetwork();

        // Pre-serialize the network to simulate a Redis L2 hit
        RoadNetworkProviderAdapter.NodeDto nodeDto = new RoadNetworkProviderAdapter.NodeDto(
                "depot-1", TENANT.toString(), "DEPOT", 3.848, 11.502,
                "Depot Central", "YDE", true, 100,
                Instant.now().toString(), Instant.now().toString());
        RoadNetworkProviderAdapter.ArcDto arcDto = new RoadNetworkProviderAdapter.ArcDto(
                "arc-1", TENANT.toString(), "depot-1", "wp-1",
                5.0, "PAVED", 50.0, 1.0, true,
                Instant.now().toString(), Instant.now().toString());
        RoadNetworkProviderAdapter.NetworkDto dto = new RoadNetworkProviderAdapter.NetworkDto(
                TENANT.toString(), List.of(nodeDto), List.of(arcDto));
        String json = MAPPER.writeValueAsString(dto);

        setupRedisHit(json);

        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> {
                    assertThat(result.tenantId()).isEqualTo(TENANT);
                    assertThat(result.nodeCount()).isEqualTo(1);
                    assertThat(result.arcCount()).isEqualTo(1);
                    assertThat(result.findNode(RoadNodeId.of("depot-1"))).isPresent();
                })
                .verifyComplete();

        // DB should NOT be called when L2 has data
        verify(roadNetworkUseCase, never()).loadNetwork(TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Redis down → graceful degradation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_redisDown_fallsBackToDatabase() {
        RoadNetwork network = buildSampleNetwork();
        setupRedisDown();
        when(roadNetworkUseCase.loadNetwork(TENANT)).thenReturn(Mono.just(network));

        // Also set up Redis write to fail
        when(valueOps.set(eq(CACHE_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> {
                    assertThat(result.nodeCount()).isEqualTo(2);
                    assertThat(result.arcCount()).isEqualTo(1);
                })
                .verifyComplete();

        // DB called as fallback
        verify(roadNetworkUseCase).loadNetwork(TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Eviction
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void evict_clearsFromL1AndL2() {
        RoadNetwork network = buildSampleNetwork();
        setupRedisMiss();
        setupRedisWriteSuccess();
        when(roadNetworkUseCase.loadNetwork(TENANT)).thenReturn(Mono.just(network));

        // Populate L1 cache
        adapter.loadNetwork(TENANT).block();

        // Set up eviction mocks
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(Mono.just(1L));

        // Evict
        StepVerifier.create(adapter.evict(TENANT))
                .verifyComplete();

        verify(redisTemplate).delete(CACHE_KEY);

        // After eviction, next call should hit DB again
        setupRedisMiss();
        setupRedisWriteSuccess();
        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> assertThat(result.nodeCount()).isEqualTo(2))
                .verifyComplete();

        // DB called twice: once before eviction, once after
        verify(roadNetworkUseCase, times(2)).loadNetwork(TENANT);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Serialization round-trip
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void serializationRoundTrip_preservesNetworkIntegrity() throws Exception {
        RoadNetwork original = buildSampleNetwork();

        // Use reflection to access the private serialization methods
        var serializeMethod = RoadNetworkProviderAdapter.class
                .getDeclaredMethod("serializeNetwork", RoadNetwork.class);
        serializeMethod.setAccessible(true);
        String json = ((Mono<String>) serializeMethod.invoke(adapter, original)).block();

        var deserializeMethod = RoadNetworkProviderAdapter.class
                .getDeclaredMethod("deserializeNetwork", String.class);
        deserializeMethod.setAccessible(true);
        RoadNetwork restored = ((Mono<RoadNetwork>) deserializeMethod.invoke(adapter, json)).block();

        assertThat(restored).isNotNull();
        assertThat(restored.tenantId()).isEqualTo(original.tenantId());
        assertThat(restored.nodeCount()).isEqualTo(original.nodeCount());
        assertThat(restored.arcCount()).isEqualTo(original.arcCount());

        // Verify adjacency list was rebuilt correctly
        for (RoadNodeId nodeId : original.nodes().keySet()) {
            assertThat(restored.outgoingArcs(nodeId))
                    .hasSameSizeAs(original.outgoingArcs(nodeId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Null-safety
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_nullTenantId_throwsNPE() {
        StepVerifier.create(adapter.loadNetwork(null))
                .expectError(NullPointerException.class)
                .verify();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Empty network
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void loadNetwork_emptyNetwork_stillCached() {
        RoadNetwork emptyNetwork = RoadNetwork.build(TENANT, List.of(), List.of());
        setupRedisMiss();
        setupRedisWriteSuccess();
        when(roadNetworkUseCase.loadNetwork(TENANT)).thenReturn(Mono.just(emptyNetwork));

        StepVerifier.create(adapter.loadNetwork(TENANT))
                .assertNext(result -> {
                    assertThat(result.nodeCount()).isZero();
                    assertThat(result.arcCount()).isZero();
                })
                .verifyComplete();

        // Verify Redis write was attempted (empty networks are cached)
        verify(valueOps).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }
}
