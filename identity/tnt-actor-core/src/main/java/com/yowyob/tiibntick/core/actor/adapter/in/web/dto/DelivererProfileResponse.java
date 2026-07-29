package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code identity} is resolved live from the Kernel via
 * {@code IResolveActorIdentityUseCase} — never persisted on the profile
 * itself, so it can be {@code null} if the Kernel is unreachable or does not
 * (yet) expose a by-id actor lookup.
 */
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
        Instant updatedAt,
        ActorIdentitySummary identity) {

    public static DelivererProfileResponse from(DelivererProfile p) {
        return from(p, null);
    }

    public static DelivererProfileResponse from(DelivererProfile p, ActorIdentitySummary identity) {
        return new DelivererProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorType().name(), p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.rating().score(), p.rating().totalRatings(),
                p.agencyId(), p.branchId(), p.vehicleId(), p.missionActiveId(),
                p.capacityKg(), p.delivererType().name(),
                p.isAvailableForMission(),
                p.createdAt(), p.updatedAt(), identity);
    }
}
