package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for LoyaltyAccount.
 *
 * @author MANFOUO Braun
 */
public record LoyaltyAccountResponse(
        UUID id,
        UUID thirdPartyId,
        int availablePoints,
        int lifetimePoints,
        int redeemedPoints,
        LoyaltyTier currentTier,
        int maxDiscountXaf,
        Instant updatedAt
) {}
