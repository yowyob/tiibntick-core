package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.List;

/**
 * Value Object — defines which listings / service types a campaign targets.
 * @author MANFOUO Braun
 */
public record CampaignScope(
        boolean applyToAll,
        List<MarketListingId> targetListingIds,
        List<ServiceType> targetServiceTypes,
        List<ProviderType> targetProviderTypes
) {
    public boolean includes(MarketListingId listingId) {
        if (applyToAll) return true;
        return targetListingIds != null && targetListingIds.contains(listingId);
    }
}
