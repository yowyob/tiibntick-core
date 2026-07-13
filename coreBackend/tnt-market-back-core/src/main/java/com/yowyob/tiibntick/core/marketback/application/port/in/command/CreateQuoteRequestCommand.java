package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command — create a QuoteRequest from a client to a provider.
 * @author MANFOUO Braun
 */
public record CreateQuoteRequestCommand(
        @NotNull String tenantId,
        @NotNull UUID clientId,
        @NotNull UUID listingId,
        @NotNull UUID providerId,
        // Pickup address
        String pickupStreet, String pickupDistrict, String pickupCity,
        Double pickupLat, Double pickupLng,
        // Delivery address
        String deliveryStreet, String deliveryDistrict, String deliveryCity,
        Double deliveryLat, Double deliveryLng,
        // Parcel
        String parcelDescription,
        double weightKg,
        double lengthCm, double widthCm, double heightCm,
        double valueXaf,
        boolean fragile, boolean perishable, boolean requiresInsurance,
        int quantity,
        // Schedule
        LocalDateTime desiredPickupAt,
        LocalDateTime desiredDeliveryAt,
        @NotNull DeliveryUrgency urgency,
        String specialInstructions,
        String notes
) {}
