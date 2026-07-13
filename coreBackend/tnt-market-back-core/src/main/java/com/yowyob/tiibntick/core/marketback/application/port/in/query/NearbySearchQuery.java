package com.yowyob.tiibntick.core.marketback.application.port.in.query;

/**
 * Query — find relay points or providers near a geo coordinate.
 * @author MANFOUO Braun
 */
public record NearbySearchQuery(
        String tenantId,
        double lat, double lng,
        double radiusKm,
        int limit
) {}
