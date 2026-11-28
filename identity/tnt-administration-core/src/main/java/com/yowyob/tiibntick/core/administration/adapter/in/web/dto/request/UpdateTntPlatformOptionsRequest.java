package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * HTTP request body to update TiiBnTick platform options.
 *
 * <p>Lot L1 (spec §5.3): {@code relayPointMaxStorageHours} was removed — it was never read; the relay storage
 * duration is now the platform delivery threshold {@code relayStorageDays}
 * ({@code /api/v1/admin/settings/delivery-thresholds}). The column stays in place (non-destructive) and keeps its
 * stored value. A client that still sends the field is tolerated: it is ignored, not rejected.
 *
 * Author: MANFOUO Braun
 */
@JsonIgnoreProperties({"relayPointMaxStorageHours"})
public record UpdateTntPlatformOptionsRequest(
        boolean blockchainEnabled,
        boolean smartDisputeResolutionEnabled,
        String blockchainNetwork,
        boolean freelancerModeEnabled,
        boolean requireFreelancerApproval,
        @Positive int maxFreelancerConcurrentMissions,
        boolean pointRelaisModeEnabled,
        boolean announcementMarketplaceEnabled,
        @Positive int maxCourierAnnouncementResponses,
        BigDecimal tvaRate,
        @NotBlank String defaultCurrency,
        boolean disputeManagementEnabled,
        @Positive int disputeFilingWindowDays
) {}
