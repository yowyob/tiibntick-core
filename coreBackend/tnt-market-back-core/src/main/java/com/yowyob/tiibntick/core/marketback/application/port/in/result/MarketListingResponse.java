package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.ListingStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO — MarketListing representation.
 * @author MANFOUO Braun
 */
public record MarketListingResponse(
        UUID id,
        String tenantId,
        UUID providerId,
        ProviderType providerType,
        ListingStatus status,
        String displayName,
        String tagline,
        String description,
        String logoKey,
        String bannerKey,
        String contactPhone,
        String contactEmail,
        String websiteUrl,
        List<String> cities,
        Double radiusKm,
        Double centerLat,
        Double centerLng,
        String seoSlug,
        double averageRating,
        int totalReviews,
        long viewCount,
        long conversionCount,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
