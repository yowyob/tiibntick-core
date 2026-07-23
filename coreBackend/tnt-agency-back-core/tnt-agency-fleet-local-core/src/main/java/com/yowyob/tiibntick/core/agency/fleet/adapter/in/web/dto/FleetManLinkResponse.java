package com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record FleetManLinkResponse(
        UUID agencyId,
        UUID tenantId,
        String fleetmanUserId,
        String fleetmanFleetId,
        String email,
        String refreshTokenEnc,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
