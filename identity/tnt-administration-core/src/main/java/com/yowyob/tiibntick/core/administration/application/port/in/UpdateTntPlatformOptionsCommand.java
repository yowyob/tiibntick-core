package com.yowyob.tiibntick.core.administration.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to update TiiBnTick platform options for a tenant.
 * Author: MANFOUO Braun
 */
public record UpdateTntPlatformOptionsCommand(
        @NotNull UUID tenantId,
        @NotNull UUID actorUserId,
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
        String defaultCurrency,
        boolean disputeManagementEnabled,
        @Positive int disputeFilingWindowDays
) {}
