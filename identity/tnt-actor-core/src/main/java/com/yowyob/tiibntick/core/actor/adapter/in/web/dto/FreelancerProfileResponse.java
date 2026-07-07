package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        Instant updatedAt) {

    public static FreelancerProfileResponse from(FreelancerProfile p) {
        return new FreelancerProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.hasLocation() ? p.currentLocation().latitude() : null,
                p.hasLocation() ? p.currentLocation().longitude() : null,
                p.rating().score(), p.rating().totalRatings(),
                p.serviceZoneIds().stream().map(ServiceZoneId::value).toList(),
                p.availabilitySlots().stream().map(AvailabilitySlotDto::from).toList(),
                p.associatedAgencyIds(),
                p.createdAt(), p.updatedAt());
    }
}
