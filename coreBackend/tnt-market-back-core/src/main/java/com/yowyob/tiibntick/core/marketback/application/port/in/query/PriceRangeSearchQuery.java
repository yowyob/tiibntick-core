package com.yowyob.tiibntick.core.marketback.application.port.in.query;

/**
 * Query — search listings within a price range.
 * @author MANFOUO Braun
 */
public record PriceRangeSearchQuery(
        String tenantId,
        String city,
        long minPriceXaf,
        long maxPriceXaf,
        double weightKg,
        double distanceKm,
        int page, int size
) {}
