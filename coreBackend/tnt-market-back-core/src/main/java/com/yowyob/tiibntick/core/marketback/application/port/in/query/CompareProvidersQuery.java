package com.yowyob.tiibntick.core.marketback.application.port.in.query;

import java.util.List;
import java.util.UUID;

/**
 * Query — compare multiple providers by their listings.
 * @author MANFOUO Braun
 */
public record CompareProvidersQuery(
        String tenantId,
        List<UUID> listingIds,
        double weightKg,
        double distanceKm,
        boolean fragile,
        boolean express
) {}
