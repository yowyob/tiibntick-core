package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoZone;

public final class DaoZoneResponseMapper {

    private DaoZoneResponseMapper() {
    }

    public static DaoZoneResponse toResponse(DaoZone zone) {
        return new DaoZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getDescription(),
                zone.getCenter().latitude(),
                zone.getCenter().longitude(),
                zone.getRadiusKm(),
                zone.getStatus().name(),
                zone.getCreatedBy(),
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }
}
