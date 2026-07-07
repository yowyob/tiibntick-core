package com.yowyob.tiibntick.core.product.application.port.in;

import com.yowyob.tiibntick.core.product.domain.model.ServiceType;

import java.util.UUID;

/**
 * Command to create a new TNT service offer.
 *
 * <p>{@code catalogProductId} is optional: when provided, the offer will be linked to the
 * Kernel catalog product (RT-comops-product-core) it is designed to transport. This enables
 * product-aware matching and pricing. When null, the offer is a general-purpose logistics offer.
 *
 * @author MANFOUO Braun
 */
public record CreateServiceOfferCommand(
        UUID tenantId,
        UUID providerId,
        /**
         * Optional UUID of the Kernel catalog product this offer is designed for.
         * Null for general-purpose offers not tied to a specific product type.
         */
        UUID catalogProductId,
        String name,
        String description,
        ServiceType type,
        double maxWeightKg,
        Double maxDistanceKm,
        int deliveryWindowHours,
        UUID coverageZoneId,
        String policyId
) {
    /** Backward-compatible constructor without catalogProductId. */
    public CreateServiceOfferCommand(UUID tenantId, UUID providerId, String name, String description,
                                      ServiceType type, double maxWeightKg, Double maxDistanceKm,
                                      int deliveryWindowHours, UUID coverageZoneId, String policyId) {
        this(tenantId, providerId, null, name, description, type,
             maxWeightKg, maxDistanceKm, deliveryWindowHours, coverageZoneId, policyId);
    }
}
