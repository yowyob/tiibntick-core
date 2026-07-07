package com.yowyob.tiibntick.core.geo.domain;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for domain model: GeoPoint, RoadNode, RoadArc, RoadNetwork, ServiceZonePolygon.
 *
 * Author: MANFOUO Braun
 */
class RoadNetworkTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void geoPoint_haversineDistance_yaoundeToDschang() {
        GeoPoint yaounde  = GeoPoint.of(3.848, 11.502);
        GeoPoint dschang  = GeoPoint.of(5.442, 10.053);
        double distance = yaounde.haversineDistanceTo(dschang);
        // Approximate airline distance ~225 km
        assertThat(distance).isBetween(200.0, 260.0);
    }

    @Test
    void geoPoint_equidistantPoints_returnsZero() {
        GeoPoint p = GeoPoint.of(4.0, 9.7);
        assertThat(p.haversineDistanceTo(p)).isEqualTo(0.0);
    }

    @Test
    void geoPoint_invalidLatitude_throwsException() {
        assertThatThrownBy(() -> GeoPoint.of(91.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    void geoPoint_wktRoundTrip() {
        GeoPoint original = GeoPoint.of(3.848, 11.502);
        String wkt = original.toWkt();
        GeoPoint parsed = GeoPoint.fromWkt(wkt);
        assertThat(parsed.latitude()).isEqualTo(original.latitude());
        assertThat(parsed.longitude()).isEqualTo(original.longitude());
    }

    @Test
    void roadNode_create_isActiveByDefault() {
        RoadNode node = RoadNode.create(TENANT, NodeType.DEPOT,
                GeoPoint.of(3.848, 11.502), "Depot Central Yaoundé", "YDE", 100);
        assertThat(node.isActive()).isTrue();
        assertThat(node.type()).isEqualTo(NodeType.DEPOT);
        assertThat(node.cityCode()).isEqualTo("YDE");
    }

    @Test
    void roadNode_deactivate_setsActiveFalse() {
        RoadNode node = RoadNode.create(TENANT, NodeType.WAYPOINT,
                GeoPoint.of(3.9, 11.5), "Carrefour Mvog-Ada", "YDE", null);
        node.deactivate();
        assertThat(node.isActive()).isFalse();
    }

    @Test
    void roadArc_effectiveSpeed_accountsForTraffic() {
        RoadArc arc = RoadArc.createWithSpeed(TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                5.0, RoadType.PAVED, 50.0, false);
        arc.updateTrafficFactor(2.0);
        assertThat(arc.effectiveSpeedKmh()).isEqualTo(25.0);
        assertThat(arc.travelTimeHours()).isEqualTo(0.2);
    }

    @Test
    void roadArc_dirtRoad_hasMaxPenibility() {
        RoadArc arc = RoadArc.create(TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                10.0, RoadType.DIRT, false);
        assertThat(arc.penibility()).isEqualTo(1.0);
    }

    @Test
    void roadNetwork_build_adjacencyListContainsBidirectionalEdges() {
        //RoadNodeId n1 = RoadNodeId.generate();
        //RoadNodeId n2 = RoadNodeId.generate();
        RoadNode node1 = RoadNode.create(TENANT, NodeType.DEPOT, GeoPoint.of(3.8, 11.5), "N1", "YDE", null);
        RoadNode node2 = RoadNode.create(TENANT, NodeType.CLIENT_POINT, GeoPoint.of(3.9, 11.6), "N2", "YDE", null);

        RoadArc arc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT, node1.id(), node2.id(),
                5.0, RoadType.PAVED, 50.0, 1.0, true,
                java.time.Instant.now(), java.time.Instant.now());

        RoadNetwork network = RoadNetwork.build(TENANT, List.of(node1, node2), List.of(arc));

        assertThat(network.outgoingArcs(node1.id())).hasSize(1);
        assertThat(network.outgoingArcs(node2.id())).hasSize(1);
        assertThat(network.nodeCount()).isEqualTo(2);
    }

    @Test
    void serviceZonePolygon_containsPoint_insideReturnsTrue() {
        List<GeoPoint> vertices = List.of(
                GeoPoint.of(3.8, 11.4),
                GeoPoint.of(3.8, 11.6),
                GeoPoint.of(4.0, 11.6),
                GeoPoint.of(4.0, 11.4)
        );
        ServiceZonePolygon zone = ServiceZonePolygon.create(TENANT, UUID.randomUUID(), "TestZone", vertices);
        assertThat(zone.contains(GeoPoint.of(3.9, 11.5))).isTrue();
    }

    @Test
    void serviceZonePolygon_containsPoint_outsideReturnsFalse() {
        List<GeoPoint> vertices = List.of(
                GeoPoint.of(3.8, 11.4),
                GeoPoint.of(3.8, 11.6),
                GeoPoint.of(4.0, 11.6),
                GeoPoint.of(4.0, 11.4)
        );
        ServiceZonePolygon zone = ServiceZonePolygon.create(TENANT, UUID.randomUUID(), "TestZone", vertices);
        assertThat(zone.contains(GeoPoint.of(5.0, 11.5))).isFalse();
    }

    @Test
    void relayHub_addParcel_updatesOccupancy() {
        RelayHub hub = RelayHub.create(TENANT, UUID.randomUUID(),
                RoadNodeId.generate(), 10, "actor-001");
        hub.addParcel();
        assertThat(hub.currentOccupancy()).isEqualTo(1);
        assertThat(hub.status()).isEqualTo(HubStatus.ACTIVE);
    }

    @Test
    void relayHub_fillToCapacity_statusBecomeFull() {
        RelayHub hub = RelayHub.create(TENANT, UUID.randomUUID(),
                RoadNodeId.generate(), 1, "actor-001");
        hub.addParcel();
        assertThat(hub.status()).isEqualTo(HubStatus.FULL);
    }

    @Test
    void costWeights_defaultWeights_sumToOne() {
        CostWeights w = CostWeights.defaultWeights();
        double sum = w.alpha() + w.beta() + w.gamma() + w.delta() + w.eta();
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void costWeights_negativeWeight_throwsException() {
        assertThatThrownBy(() -> CostWeights.of(-0.1, 0.4, 0.2, 0.2, 0.3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weatherCondition_heavyRain_returnsMaxPhiRain() {
        WeatherCondition weather = WeatherCondition.of(15.0, 30.0, java.time.Instant.now());
        assertThat(weather.phiRain()).isEqualTo(0.6);
        assertThat(weather.isRaining()).isTrue();
    }

    @Test
    void weatherCondition_clear_returnsZeroPhi() {
        WeatherCondition weather = WeatherCondition.clear(java.time.Instant.now());
        assertThat(weather.phiRain()).isEqualTo(0.0);
        assertThat(weather.isRaining()).isFalse();
    }
}
