package com.yowyob.tiibntick.core.marketback.application.port.in.query;

import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;

/**
 * Query — multi-criteria marketplace search.
 * @author MANFOUO Braun
 */
public record MarketSearchQuery(
        String tenantId,
        String keyword,
        String city,
        String district,
        ProviderType providerType,
        ServiceType serviceType,
        Double minRating,
        Long maxPriceXaf,
        Double lat, Double lng, Double radiusKm,
        String sortBy,
        int page, int size
) {}
