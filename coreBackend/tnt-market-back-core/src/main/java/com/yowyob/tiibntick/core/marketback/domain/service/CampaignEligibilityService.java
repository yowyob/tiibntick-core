package com.yowyob.tiibntick.core.marketback.domain.service;

import com.yowyob.tiibntick.core.marketback.domain.model.*;

import java.time.LocalDateTime;

/**
 * Domain service — evaluates whether a campaign discount applies to a given order.
 *
 * @author MANFOUO Braun
 */
public class CampaignEligibilityService {

    /**
     * Returns true if the campaign is currently active and applicable to the given
     * provider and service type.
     */
    public boolean isEligible(MarketCampaign campaign, MarketListingId listingId, ServiceType serviceType, Money orderAmount) {
        if (campaign.getStatus() != CampaignStatus.ACTIVE) return false;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartDate()) || now.isAfter(campaign.getEndDate())) return false;
        CampaignScope scope = campaign.getScope();
        if (!scope.applyToAll()) {
            if (!scope.includes(listingId)) return false;
            if (scope.targetServiceTypes() != null && !scope.targetServiceTypes().isEmpty()
                    && !scope.targetServiceTypes().contains(serviceType)) return false;
        }
        // Check budget remaining
        if (campaign.getBudget() != null
                && campaign.getSpentAmount() != null
                && campaign.getSpentAmount().amount() >= campaign.getBudget().amount()) {
            return false;
        }
        return true;
    }

    /**
     * Computes the actual discount amount to apply.
     */
    public Money applyDiscount(MarketCampaign campaign, Money orderAmount) {
        return campaign.computeDiscount(orderAmount);
    }
}
