package com.yowyob.tiibntick.core.marketback.application.port.in.result;

import com.yowyob.tiibntick.core.marketback.domain.model.CampaignStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.CampaignType;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO — MarketCampaign.
 * @author MANFOUO Braun
 */
public record MarketCampaignResponse(
        UUID id,
        String tenantId,
        String name,
        CampaignType type,
        CampaignStatus status,
        DiscountType discountType,
        double discountValue,
        String promoCode,
        int usageCount,
        int maxUsage,
        LocalDateTime startDate,
        LocalDateTime endDate,
        LocalDateTime createdAt
) {}
