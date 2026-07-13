package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

/**
 * Command — create a ServiceOffer for a listing.
 * @author MANFOUO Braun
 */
public record CreateServiceOfferCommand(
        @NotBlank String tenantId,
        @NotNull UUID listingId,
        @NotNull UUID providerId,
        @NotBlank String name,
        String description,
        @NotNull ServiceType serviceType,
        // Pricing
        long basePriceXaf,
        long perKmRateXaf,
        long perKgRateXaf,
        long minimumPriceXaf,
        long maximumPriceXaf,
        String pricingDslExpression,
        // Constraints
        double maxWeightKg,
        double maxLengthCm,
        double maxWidthCm,
        double maxHeightCm,
        double maxValueXaf,
        boolean acceptsFragile,
        boolean acceptsPerishable,
        double maxDistanceKm,
        // Availability
        Set<DayOfWeek> daysOfWeek,
        String openTime,
        String closeTime,
        boolean expressAvailable,
        boolean sameDayAvailable
) {}
