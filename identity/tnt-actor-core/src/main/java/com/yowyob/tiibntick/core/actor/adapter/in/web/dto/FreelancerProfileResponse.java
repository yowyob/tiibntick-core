package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code identity} is resolved live from the Kernel via
 * {@code IResolveActorIdentityUseCase} — never persisted on the profile
 * itself, so it can be {@code null} if the Kernel is unreachable or does not
 * (yet) expose a by-id actor lookup.
 */
public record FreelancerProfileResponse(
        UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        double ratingScore,
        int ratingTotal,
        List<UUID> serviceZoneIds,
        List<AvailabilitySlotDto> availabilitySlots,
        Set<UUID> associatedAgencyIds,
        Instant createdAt,
        Instant updatedAt,
        ActorIdentitySummary identity) {

    public static FreelancerProfileResponse from(FreelancerProfile p) {
        return from(p, null);
    }

    public static FreelancerProfileResponse from(FreelancerProfile p, ActorIdentitySummary identity) {
        return new FreelancerProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.rating().score(), p.rating().totalRatings(),
                p.serviceZoneIds().stream().map(ServiceZoneId::value).toList(),
                p.availabilitySlots().stream().map(AvailabilitySlotDto::from).toList(),
                p.associatedAgencyIds(),
                p.createdAt(), p.updatedAt(), identity);
    }
}
