package com.yowyob.tiibntick.core.marketback.domain.model;

/**
 * Value Object — runtime context passed to the billing DSL evaluator.
 * @author MANFOUO Braun
 */
public record PricingContext(
        double distanceKm,
        double weightKg,
        double volumetricWeightKg,
        double valueXaf,
        boolean fragile,
        boolean perishable,
        boolean requiresInsurance,
        boolean express,
        boolean sameDay,
        ServiceType serviceType,
        String city,
        String tenantId
) {
    public static PricingContext from(DeliveryRequest request, ServiceType serviceType, String tenantId) {
        ParcelSpec p = request.parcelSpec();
        return new PricingContext(
                request.distanceKm(),
                p.weightKg(),
                p.volumetricWeightKg(),
                p.valueXaf(),
                p.fragile(),
                p.perishable(),
                p.requiresInsurance(),
                request.urgency() == DeliveryUrgency.EXPRESS,
                request.urgency() == DeliveryUrgency.SAME_DAY,
                serviceType,
                request.pickupAddress().city(),
                tenantId
        );
    }
}
