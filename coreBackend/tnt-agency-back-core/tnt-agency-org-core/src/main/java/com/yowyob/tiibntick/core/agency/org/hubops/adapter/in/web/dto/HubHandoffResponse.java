package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record HubHandoffResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        UUID hubId,
        String handoffType,
        String status,
        UUID missionId,
        UUID packageId,
        String trackingCode,
        UUID requesterActorId,
        String requesterRole,
        String requesterLabel,
        String withdrawParty,
        UUID validatedByActorId,
        String validatedByLabel,
        String notes,
        Instant createdAt,
        Instant validatedAt,
        Instant completedAt) {}
