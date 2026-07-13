package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import com.yowyob.tiibntick.core.marketback.domain.model.CampaignType;
import com.yowyob.tiibntick.core.marketback.domain.model.DiscountType;
import com.yowyob.tiibntick.core.marketback.domain.model.ProviderType;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Command — admin creates a promotional campaign.
 * @author MANFOUO Braun
 */
public record CreateCampaignCommand(
        @NotBlank String tenantId,
        @NotNull UUID adminId,
        @NotBlank String name,
        String description,
        @NotNull CampaignType type,
        @NotNull DiscountType discountType,
        double discountValue,
        long maxDiscountXaf,
        long minimumOrderXaf,
        boolean applyToAll,
        List<UUID> targetListingIds,
        List<ServiceType> targetServiceTypes,
        List<ProviderType> targetProviderTypes,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int maxUsage,
        String promoCode
) {}
