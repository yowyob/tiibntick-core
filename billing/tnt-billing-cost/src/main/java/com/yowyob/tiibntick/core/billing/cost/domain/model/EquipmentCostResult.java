package com.yowyob.tiibntick.core.billing.cost.domain.model;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.EquipmentCostType;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * Value object representing the result of computing equipment operational costs
 * for a FreelancerOrg delivery mission.
 *
 * <p>This cost is added to the {@code otherCosts} component of {@link OperationalCost}.
 *
 * @author MANFOUO Braun
 */
@Builder
public record EquipmentCostResult(
        /**
         * Individual cost contribution per equipment type.
         */
        Map<String, BigDecimal> breakdownByType,

        /**
         * Total equipment cost for the mission (sum of all equipment costs).
         */
        BigDecimal totalCostXaf,

        /**
         * Currency code (always XAF in Cameroon context).
         */
        String currencyCode,

        /**
         * Distance used for the variable cost calculation.
         */
        double distanceKm,

        /**
         * Number of equipment pieces that had costs.
         */
        int equipmentCount
) {
    /**
     * Computes the equipment cost for a set of deployed equipment types.
     *
     * @param equipmentTypeNames set of equipment type names (from EquipmentCostType)
     * @param distanceKm         delivery distance in km
     * @return equipment cost result
     */
    public static EquipmentCostResult compute(Set<String> equipmentTypeNames, double distanceKm) {
        Map<String, BigDecimal> breakdown = new java.util.LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        for (String typeName : equipmentTypeNames) {
            EquipmentCostType type = resolveType(typeName);
            if (type != null) {
                BigDecimal cost = BigDecimal.valueOf(type.totalCostXaf(distanceKm))
                        .setScale(2, RoundingMode.HALF_UP);
                breakdown.put(typeName, cost);
                total = total.add(cost);
                count++;
            }
        }

        return EquipmentCostResult.builder()
                .breakdownByType(breakdown)
                .totalCostXaf(total.setScale(2, RoundingMode.HALF_UP))
                .currencyCode("XAF")
                .distanceKm(distanceKm)
                .equipmentCount(count)
                .build();
    }

    public static EquipmentCostResult zero() {
        return EquipmentCostResult.builder()
                .breakdownByType(Map.of())
                .totalCostXaf(BigDecimal.ZERO)
                .currencyCode("XAF")
                .distanceKm(0.0)
                .equipmentCount(0)
                .build();
    }

    private static EquipmentCostType resolveType(String name) {
        try {
            return EquipmentCostType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // Unknown type — skip
        }
    }
}
