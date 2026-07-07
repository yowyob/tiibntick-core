package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.geo.application.service.CostFunctionService;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import com.yowyob.tiibntick.core.route.application.port.out.IRoadNetworkProvider;
import com.yowyob.tiibntick.core.route.domain.exception.PathNotFoundException;
import com.yowyob.tiibntick.core.route.domain.model.RoutePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AStarPathfinderServiceTest {

    @Mock private IRoadNetworkProvider networkProvider;
    @Mock private CostFunctionService costFunctionService;

    private AStarPathfinderService service;
    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AStarPathfinderService(networkProvider, costFunctionService);
    }

    private RoadNetwork buildGrid3x3() {
        List<RoadNode> nodes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            double lat = 3.8 + (i / 3) * 0.01;
            double lng = 11.5 + (i % 3) * 0.01;
            nodes.add(RoadNode.rehydrate(RoadNodeId.of("N" + i), TENANT, NodeType.WAYPOINT,
                    GeoPoint.of(lat, lng), "Node" + i, "YDE", true, null,
                    Instant.now(), Instant.now()));
        }
        List<RoadArc> arcs = new ArrayList<>();
        int[][] edges = {{0,1},{1,2},{3,4},{4,5},{6,7},{7,8},{0,3},{1,4},{2,5},{3,6},{4,7},{5,8}};
        for (int[] e : edges) {
            arcs.add(RoadArc.rehydrate(RoadArcId.generate(), TENANT,
                    RoadNodeId.of("N" + e[0]), RoadNodeId.of("N" + e[1]),
                    1.0, RoadType.PAVED, 50.0, 1.0, true,
                    Instant.now(), Instant.now()));
        }
        return RoadNetwork.build(TENANT, nodes, arcs);
    }

    @Test
    void astar_grid3x3_findsShortestPath() {
        RoadNetwork network = buildGrid3x3();
        when(networkProvider.loadNetwork(TENANT)).thenReturn(Mono.just(network));
        when(costFunctionService.computeCost(any(), any(), any())).thenReturn(1.0);

        StepVerifier.create(
                service.findShortestPath("N0", "N8", CostWeights.defaultWeights(), TENANT)
        ).assertNext(path -> {
            assertThat(path.isEmpty()).isFalse();
            assertThat(path.origin().value()).isEqualTo("N0");
            assertThat(path.destination().value()).isEqualTo("N8");
            assertThat(path.nodeCount()).isLessThanOrEqualTo(5);
        }).verifyComplete();
    }

    @Test
    void astar_sameOriginAndDest_returnsEmptyPath() {
        RoadNetwork network = buildGrid3x3();
        when(networkProvider.loadNetwork(TENANT)).thenReturn(Mono.just(network));

        StepVerifier.create(
                service.findShortestPath("N4", "N4", CostWeights.defaultWeights(), TENANT)
        ).assertNext(path -> {
            assertThat(path.nodeCount()).isEqualTo(1);
            assertThat(path.totalDistanceKm()).isEqualTo(0.0);
        }).verifyComplete();
    }

    @Test
    void astar_directCompute_findsPath() {
        RoadNetwork network = buildGrid3x3();
        WeatherCondition clear = WeatherCondition.clear(Instant.now());
        when(costFunctionService.computeCost(any(), any(), any())).thenReturn(1.0);

        RoutePath path = service.computeAStar(network, RoadNodeId.of("N0"),
                RoadNodeId.of("N8"), CostWeights.defaultWeights(), clear);

        assertThat(path.isEmpty()).isFalse();
        assertThat(path.totalCompositeCost()).isGreaterThan(0);
    }

    @Test
    void astar_unreachableNode_throwsException() {
        RoadNode isolated = RoadNode.rehydrate(RoadNodeId.of("ISOLATED"), TENANT,
                NodeType.WAYPOINT, GeoPoint.of(5, 12), "Isolated", "DLA", true, null,
                Instant.now(), Instant.now());
        RoadNode n0 = RoadNode.rehydrate(RoadNodeId.of("N0"), TENANT,
                NodeType.WAYPOINT, GeoPoint.of(3.8, 11.5), "N0", "YDE", true, null,
                Instant.now(), Instant.now());
        RoadNetwork network = RoadNetwork.build(TENANT, List.of(isolated, n0), List.of());

        lenient().when(costFunctionService.computeCost(any(), any(), any())).thenReturn(1.0);

        assertThatThrownBy(() ->
                service.computeAStar(network, RoadNodeId.of("N0"), RoadNodeId.of("ISOLATED"),
                        CostWeights.defaultWeights(), WeatherCondition.clear(Instant.now()))
        ).isInstanceOf(PathNotFoundException.class);
    }
}
