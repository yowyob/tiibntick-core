package com.yowyob.tiibntick.core.route.domain.model;

import com.yowyob.tiibntick.core.geo.domain.model.RoadArcId;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;

public record RouteSegment(
        RoadNodeId fromNodeId,
        RoadNodeId toNodeId,
        RoadArcId arcId,
        double distanceKm,
        double partialCost
) {}
