package com.yowyob.tiibntick.core.route.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import java.time.Instant;

public record RouteWaypoint(
        int sequenceOrder,
        String nodeId,
        WaypointType type,
        GeoPoint coordinates,
        String missionItemRef,
        Instant estimatedArrivalAt,
        Instant actualArrivalAt,
        int dwellTimeMinutes
) {
    public RouteWaypoint withActualArrival(Instant actual) {
        return new RouteWaypoint(sequenceOrder, nodeId, type, coordinates,
                missionItemRef, estimatedArrivalAt, actual, dwellTimeMinutes);
    }
}
