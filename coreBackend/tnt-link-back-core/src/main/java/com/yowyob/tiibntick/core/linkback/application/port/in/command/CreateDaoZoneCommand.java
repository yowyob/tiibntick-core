package com.yowyob.tiibntick.core.linkback.application.port.in.command;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;

import java.util.UUID;

public record CreateDaoZoneCommand(
        UUID tenantId,
        String name,
        String description,
        GeoPoint center,
        double radiusKm,
        UUID createdBy
) {
}
