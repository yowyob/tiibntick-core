package com.yowyob.tiibntick.core.route.domain;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.route.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class RouteModelsTest {

    @Test
    void routePath_residualCost_calculatedFromNode() {
        RoadNodeId n1 = RoadNodeId.of("A");
        RoadNodeId n2 = RoadNodeId.of("B");
        RoadNodeId n3 = RoadNodeId.of("C");
        List<RouteSegment> segs = List.of(
                new RouteSegment(n1, n2, null, 5.0, 0.3),
                new RouteSegment(n2, n3, null, 5.0, 0.4)
        );
        RoutePath path = RoutePath.of(List.of(n1, n2, n3), segs, 10.0, 0.7);
        assertThat(path.residualCostFrom(n1)).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void tour_lifecycle_transitions() {
        Tour tour = Tour.create(UUID.randomUUID(), "deliverer-1", List.of(
                new TourStop("N1", WaypointType.ORIGIN_DEPOT, 0, Instant.now(), 0, null),
                new TourStop("N2", WaypointType.DELIVERY_POINT, 1, Instant.now().plusSeconds(600), 5, "D1")
        ), 10.0, 5.0, LocalDate.now());

        assertThat(tour.status()).isEqualTo(TourStatus.PLANNED);
        tour.start();
        assertThat(tour.status()).isEqualTo(TourStatus.IN_PROGRESS);
        tour.complete();
        assertThat(tour.status()).isEqualTo(TourStatus.COMPLETED);
    }

    @Test
    void vrpSolution_isFallback_returnsCorrectStatus() {
        VrpSolution s = new VrpSolution(List.of(), 10.0, List.of(),
                SolverStatus.FALLBACK_USED, 500, null);
        assertThat(s.isFallback()).isTrue();
        assertThat(s.isOptimal()).isFalse();
    }

    @Test
    void gpsMeasurement_negativeSpeed_throwsException() {
        assertThatThrownBy(() ->
                new GPSMeasurement(GeoPoint.of(3.8, 11.5), -5.0, 0, 10.0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etaResult_remainingMinutes_calculatedCorrectly() {
        Instant now = Instant.now();
        EtaResult eta = new EtaResult(now.plusSeconds(1800), now.plusSeconds(1200),
                now.plusSeconds(2400), 0.80, now);
        assertThat(eta.remainingMinutes(now)).isEqualTo(30);
        assertThat(eta.isExpired(now)).isFalse();
    }

    @Test
    void deliveryItem_negativeWeight_throwsException() {
        assertThatThrownBy(() ->
                new DeliveryItem("D1", "N1", "N2", -1.0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
