package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import java.util.List;
import java.util.Map;

/**
 * Command — update an existing MarketListing.
 * @author MANFOUO Braun
 */
public record UpdateMarketListingCommand(
        String displayName,
        String tagline,
        String description,
        String contactEmail,
        String contactPhone,
        String websiteUrl,
        Map<String, String> socialLinks,
        List<String> certificationIds,
        Integer foundedYear,
        List<String> cities,
        Double radiusKm,
        Double centerLat,
        Double centerLng
) {}
