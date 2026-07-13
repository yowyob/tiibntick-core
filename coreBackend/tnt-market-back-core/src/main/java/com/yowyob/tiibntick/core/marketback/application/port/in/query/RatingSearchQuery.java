package com.yowyob.tiibntick.core.marketback.application.port.in.query;

/**
 * Query — filter listings by minimum average rating.
 * @author MANFOUO Braun
 */
public record RatingSearchQuery(
        String tenantId,
        String city,
        double minRating,
        int page, int size
) {}
