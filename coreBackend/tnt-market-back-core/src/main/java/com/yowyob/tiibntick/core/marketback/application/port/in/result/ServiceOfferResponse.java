package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.OfferStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO — ServiceOffer.
 * @author MANFOUO Braun
 */
public record ServiceOfferResponse(
        UUID id,
        UUID listingId,
        UUID providerId,
        String name,
        String description,
        ServiceType serviceType,
        OfferStatus status,
        long basePriceXaf,
        long perKmRateXaf,
        long perKgRateXaf,
        long minimumPriceXaf,
        double maxWeightKg,
        double maxDistanceKm,
        boolean acceptsFragile,
        boolean acceptsPerishable,
        boolean expressAvailable,
        boolean sameDayAvailable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
