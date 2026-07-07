package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Instant;
import java.util.*;

public record VrpRequest(
        String tenantId,
        List<DeliveryItem> deliveries,
        double vehicleCapacityKg,
        Double vehicleCapacityM3,
        String depotNodeId,
        List<String> availableRelayNodeIds,
        CostParams costParams,
        Map<String, Instant> deadlineByDelivery,
        int timeoutSeconds
) {
    public VrpRequest {
        Objects.requireNonNull(tenantId);
        if (deliveries == null || deliveries.isEmpty())
            throw new IllegalArgumentException("At least one delivery required");
        if (vehicleCapacityKg <= 0)
            throw new IllegalArgumentException("vehicleCapacityKg must be > 0");
        Objects.requireNonNull(depotNodeId);
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (costParams == null) costParams = CostParams.defaults();
        if (availableRelayNodeIds == null) availableRelayNodeIds = List.of();
        if (deadlineByDelivery == null) deadlineByDelivery = Map.of();
    }
}
