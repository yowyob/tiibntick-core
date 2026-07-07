package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * API response DTO for TiiBnTick platform options.
 * Author: MANFOUO Braun
 */
public record TntPlatformOptionsResponse(
        UUID id, UUID tenantId,
        boolean blockchainEnabled, boolean smartDisputeResolutionEnabled, String blockchainNetwork,
        boolean freelancerModeEnabled, boolean requireFreelancerApproval, int maxFreelancerConcurrentMissions,
        boolean pointRelaisModeEnabled, int relayPointMaxStorageHours,
        boolean announcementMarketplaceEnabled, int maxCourierAnnouncementResponses,
        BigDecimal tvaRate, String defaultCurrency,
        boolean disputeManagementEnabled, int disputeFilingWindowDays,
        Instant createdAt, Instant updatedAt) {

    public static TntPlatformOptionsResponse from(TntPlatformOptions o) {
        return new TntPlatformOptionsResponse(
                o.getId(), o.getTenantId(),
                o.isBlockchainEnabled(), o.isSmartDisputeResolutionEnabled(), o.getBlockchainNetwork(),
                o.isFreelancerModeEnabled(), o.isRequireFreelancerApproval(), o.getMaxFreelancerConcurrentMissions(),
                o.isPointRelaisModeEnabled(), o.getRelayPointMaxStorageHours(),
                o.isAnnouncementMarketplaceEnabled(), o.getMaxCourierAnnouncementResponses(),
                o.getTvaRate(), o.getDefaultCurrency(),
                o.isDisputeManagementEnabled(), o.getDisputeFilingWindowDays(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
