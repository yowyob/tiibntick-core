package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code identity} is resolved live from the Kernel via
 * {@code IResolveActorIdentityUseCase} — never persisted on the profile
 * itself, so it can be {@code null} if the Kernel is unreachable or does not
 * (yet) expose a by-id actor lookup.
 */
public record RelayOperatorProfileResponse(
        UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        double ratingScore,
        int ratingTotal,
        UUID hubId,
        List<AvailabilitySlotDto> openingHours,
        int declaredCapacityParcels,
        Instant createdAt,
        Instant updatedAt,
        ActorIdentitySummary identity) {

    public static RelayOperatorProfileResponse from(RelayOperatorProfile p) {
        return from(p, null);
    }

    public static RelayOperatorProfileResponse from(RelayOperatorProfile p, ActorIdentitySummary identity) {
        return new RelayOperatorProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.rating().score(), p.rating().totalRatings(),
                p.hubId(),
                p.openingHours().stream().map(AvailabilitySlotDto::from).toList(),
                p.declaredCapacityParcels(),
                p.createdAt(), p.updatedAt(), identity);
    }
}
