package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command — create a new MarketListing.
 * @author MANFOUO Braun
 */
public record CreateMarketListingCommand(
        @NotBlank String tenantId,
        @NotNull UUID providerId,
        @NotNull ProviderType providerType,
        UUID organizationId,
        @NotBlank String displayName,
        String tagline,
        @NotBlank String description,
        String contactEmail,
        @NotBlank String contactPhone,
        String websiteUrl,
        Map<String, String> socialLinks,
        List<String> certificationIds,
        Integer foundedYear,
        // Coverage
        List<String> cities,
        Double radiusKm,
        Double centerLat,
        Double centerLng
) {}
