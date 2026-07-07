package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;

import java.time.Instant;
import java.util.UUID;

public record DelivererProfileResponse(
        UUID id,
        UUID tenantId,
        UUID actorId,
        String actorType,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        double ratingScore,
        int ratingTotal,
        UUID agencyId,
        UUID branchId,
        UUID vehicleId,
        UUID missionActiveId,
        double capacityKg,
        String delivererType,
        boolean availableForMission,
        Instant createdAt,
        Instant updatedAt) {

    public static DelivererProfileResponse from(DelivererProfile p) {
        return new DelivererProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorType().name(), p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.rating().score(), p.rating().totalRatings(),
                p.agencyId(), p.branchId(), p.vehicleId(), p.missionActiveId(),
                p.capacityKg(), p.delivererType().name(),
                p.isAvailableForMission(),
                p.createdAt(), p.updatedAt());
    }
}
