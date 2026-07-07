package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * HTTP request body to update TiiBnTick platform options.
 * Author: MANFOUO Braun
 */
public record UpdateTntPlatformOptionsRequest(
        boolean blockchainEnabled,
        boolean smartDisputeResolutionEnabled,
        String blockchainNetwork,
        boolean freelancerModeEnabled,
        boolean requireFreelancerApproval,
        @Positive int maxFreelancerConcurrentMissions,
        boolean pointRelaisModeEnabled,
        @Positive int relayPointMaxStorageHours,
        boolean announcementMarketplaceEnabled,
        @Positive int maxCourierAnnouncementResponses,
        BigDecimal tvaRate,
        @NotBlank String defaultCurrency,
        boolean disputeManagementEnabled,
        @Positive int disputeFilingWindowDays
) {}
