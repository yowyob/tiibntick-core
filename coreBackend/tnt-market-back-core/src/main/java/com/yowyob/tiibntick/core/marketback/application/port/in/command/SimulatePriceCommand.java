package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import jakarta.validation.constraints.NotNull;

/**
 * Command — simulate delivery cost for a given parcel and route.
 * @author MANFOUO Braun
 */
public record SimulatePriceCommand(
        @NotNull String pickupCity,
        @NotNull String deliveryCity,
        double pickupLat, double pickupLng,
        double deliveryLat, double deliveryLng,
        double weightKg,
        double lengthCm, double widthCm, double heightCm,
        double valueXaf,
        boolean fragile,
        boolean perishable,
        @NotNull DeliveryUrgency urgency
) {}
