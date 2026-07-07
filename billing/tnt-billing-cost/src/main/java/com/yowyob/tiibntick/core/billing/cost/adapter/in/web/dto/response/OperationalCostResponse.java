package com.yowyob.tiibntick.core.billing.cost.adapter.in.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST response DTO for cost computation results.
 * @author MANFOUO Braun
 */
public record OperationalCostResponse(
        String missionId,
        BigDecimal fuelCost,
        BigDecimal vehicleWearCost,
        BigDecimal timeCost,
        BigDecimal penibilityCost,
        BigDecimal weatherSurcharge,
        BigDecimal otherCosts,
        BigDecimal totalCost,
        String currency,
        Map<String, Double> breakdownPercentages,
        LocalDateTime computedAt
) {}
