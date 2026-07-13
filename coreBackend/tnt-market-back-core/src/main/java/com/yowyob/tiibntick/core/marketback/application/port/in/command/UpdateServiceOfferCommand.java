package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import java.time.DayOfWeek;
import java.util.Set;

/**
 * Command — update an existing ServiceOffer.
 * @author MANFOUO Braun
 */
public record UpdateServiceOfferCommand(
        String name,
        String description,
        ServiceType serviceType,
        Long basePriceXaf,
        Long perKmRateXaf,
        Long perKgRateXaf,
        Long minimumPriceXaf,
        String pricingDslExpression,
        Double maxWeightKg,
        Boolean acceptsFragile,
        Boolean acceptsPerishable,
        Set<DayOfWeek> daysOfWeek,
        Boolean expressAvailable,
        Boolean sameDayAvailable
) {}
