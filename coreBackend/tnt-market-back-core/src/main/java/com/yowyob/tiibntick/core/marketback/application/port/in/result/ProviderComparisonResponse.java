package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO — comparison of multiple providers for a delivery request.
 * @author MANFOUO Braun
 */
public record ProviderComparisonResponse(
        List<ProviderComparisonItem> providers
) {
    public record ProviderComparisonItem(
            UUID listingId,
            String displayName,
            String providerType,
            double averageRating,
            int totalReviews,
            long estimatedPriceXaf,
            double etaHours,
            boolean expressAvailable,
            boolean sameDayAvailable,
            String logoKey
    ) {}
}
