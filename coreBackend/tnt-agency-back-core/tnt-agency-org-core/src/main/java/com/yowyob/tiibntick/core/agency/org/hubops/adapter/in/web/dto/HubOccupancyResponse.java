package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto;

import java.util.UUID;

public record HubOccupancyResponse(
        UUID hubId,
        int currentOccupancy,
        int capacityUnits,
        int availableSpace,
        boolean hasAvailableSpace,
        boolean projectedFromCore
) {}
