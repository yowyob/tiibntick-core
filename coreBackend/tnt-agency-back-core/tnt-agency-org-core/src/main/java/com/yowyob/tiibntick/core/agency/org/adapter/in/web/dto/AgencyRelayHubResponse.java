package com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AgencyRelayHubResponse(
        UUID id,
        UUID tenantId,
        UUID agencyId,
        UUID branchId,
        String name,
        String code,
        String status,
        int capacityUnits,
        int currentOccupancy,
        int availableSpace,
        Integer retentionDelayHours,
        String openingHours,
        UUID coreHubId,
        String addrCity,
        String addrCountry,
        String addrStreet,
        String addrQuarter,
        Double latitude,
        Double longitude,
        Instant createdAt) {}
