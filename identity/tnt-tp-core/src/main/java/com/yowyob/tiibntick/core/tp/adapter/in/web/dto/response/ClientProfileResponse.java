package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for TntClientProfile.
 *
 * @author MANFOUO Braun
 */
public record ClientProfileResponse(
        UUID id,
        UUID tenantId,
        UUID thirdPartyId,
        Set<TntThirdPartyRole> tntRoles,
        KycStatus kycStatus,
        boolean phoneMasked,
        Double averageRating,
        int ratingCount,
        int totalDeliveries,
        String preferredLocale,
        String preferredCurrency,
        LoyaltyTier loyaltyTier,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
