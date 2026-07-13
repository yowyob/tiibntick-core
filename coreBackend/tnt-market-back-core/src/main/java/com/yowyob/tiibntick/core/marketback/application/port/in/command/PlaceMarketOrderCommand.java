package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Command — place a MarketOrder directly from an offer (without quote flow).
 * @author MANFOUO Braun
 */
public record PlaceMarketOrderCommand(
        @NotNull String tenantId,
        @NotNull UUID clientId,
        @NotNull UUID listingId,
        @NotNull UUID providerId,
        @NotNull UUID offerId,
        // Pickup
        String pickupStreet, String pickupDistrict, String pickupCity,
        Double pickupLat, Double pickupLng,
        // Delivery
        String deliveryStreet, String deliveryDistrict, String deliveryCity,
        Double deliveryLat, Double deliveryLng,
        // Parcel
        String parcelDescription,
        double weightKg, double lengthCm, double widthCm, double heightCm,
        double valueXaf, boolean fragile, boolean perishable, boolean requiresInsurance,
        int quantity,
        DeliveryUrgency urgency,
        String specialInstructions,
        // Optional promo
        String promoCode
) {}
