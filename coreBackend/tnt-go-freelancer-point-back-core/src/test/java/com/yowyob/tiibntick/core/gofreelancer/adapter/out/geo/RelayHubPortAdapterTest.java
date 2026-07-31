package com.yowyob.tiibntick.core.gofreelancer.adapter.out.geo;

import com.yowyob.tiibntick.core.geo.application.port.in.IFindNearbyHubsUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageRoadNetworkUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNode;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the increment/decrement occupancy asymmetry flagged in review: previously
 * {@code decrementOccupancy} silently no-opped on a missing hub while {@code incrementOccupancy}
 * raised. Both must now behave symmetrically — the GOFP business layer decides whether a
 * missing hub is fatal, not this port.
 *
 * <p>Also covers {@link RelayHubPortAdapter#provisionHub} — creates the underlying road node
 * then a branch-less (freelance) hub.
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class RelayHubPortAdapterTest {

    @Mock private IFindNearbyHubsUseCase findNearbyHubsUseCase;
    @Mock private IManageRoadNetworkUseCase manageRoadNetworkUseCase;
    @Mock private IManageRelayHubUseCase manageRelayHubUseCase;

    private RelayHubPortAdapter adapter;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID hubId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new RelayHubPortAdapter(findNearbyHubsUseCase, manageRoadNetworkUseCase, manageRelayHubUseCase);
    }

    private RelayHub hub(int occupancy, int capacity) {
        return RelayHub.rehydrate(hubId, tenantId, UUID.randomUUID(), RoadNodeId.generate(),
                capacity, occupancy, "actor-1", HubStatus.ACTIVE, Instant.now(), Instant.now());
    }

    @Test
    void incrementOccupancy_hubMissing_errors() {
        when(findNearbyHubsUseCase.findHub(hubId, tenantId)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.incrementOccupancy(hubId, tenantId))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void decrementOccupancy_hubMissing_nowErrorsSymmetricallyWithIncrement() {
        when(findNearbyHubsUseCase.findHub(hubId, tenantId)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.decrementOccupancy(hubId, tenantId))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void decrementOccupancy_alreadyZero_noOpsWithoutError() {
        when(findNearbyHubsUseCase.findHub(hubId, tenantId)).thenReturn(Mono.just(hub(0, 10)));

        StepVerifier.create(adapter.decrementOccupancy(hubId, tenantId))
                .expectNextCount(1)
                .verifyComplete();

        verify(findNearbyHubsUseCase, org.mockito.Mockito.never()).updateHubOccupancy(any(), any(), any(Integer.class));
    }

    @Test
    void provisionHub_createsRoadNodeThenBranchlessHub() {
        GeoPoint point = GeoPoint.of(3.848, 11.502);
        RoadNode node = RoadNode.create(tenantId, com.yowyob.tiibntick.core.geo.domain.model.NodeType.RELAY_HUB,
                point, "Relay Point A", "YAO", 10);
        RelayHub createdHub = RelayHub.create(tenantId, null, node.id(), 10, "actor-1");

        when(manageRoadNetworkUseCase.createNode(eq(tenantId),
                eq(com.yowyob.tiibntick.core.geo.domain.model.NodeType.RELAY_HUB),
                eq(point), eq("Relay Point A"), eq("YAO"), eq(10)))
                .thenReturn(Mono.just(node));
        when(manageRelayHubUseCase.createHub(eq(tenantId), isNull(), eq(node.id().value()), eq(10), eq("actor-1")))
                .thenReturn(Mono.just(createdHub));

        StepVerifier.create(adapter.provisionHub(tenantId, point, "Relay Point A", "YAO", 10, "actor-1"))
                .expectNextMatches(h -> h.branchId() == null)
                .verifyComplete();
    }
}
