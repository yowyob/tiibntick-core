package com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Aggregated public tracking view: hub parcel + relay hub + optional mission. */
public record TrackingResponse(
        UUID id,
        UUID hubId,
        UUID missionId,
        String trackingCode,
        String status,
        Instant depositedAt,
        Instant withdrawalDeadline,
        boolean identityVerified,
        String withdrawnBy,
        Instant updatedAt,
        String hubName,
        String hubCode,
        String hubAddress,
        String hubCity,
        String hubOpeningHours,
        Double hubLatitude,
        Double hubLongitude,
        int hubAvailableSpace,
        int hubCapacity,
        String missionStatus,
        Instant missionScheduledAt,
        Instant missionStartedAt,
        Instant missionCompletedAt
) {}
