package com.yowyob.tiibntick.core.tp.domain.event;

import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Domain events emitted by tnt-tp-core aggregates.
 * These are sealed records — exhaustive event hierarchy for the domain.
 *
 * @author MANFOUO Braun
 */
public final class TntTpDomainEvents {

    private TntTpDomainEvents() {}

    // ─── Client Profile Events ────────────────────────────────────────────────

    /**
     * Published when a new TntClientProfile is registered.
     */
    public record ClientProfileRegisteredEvent(
            UUID profileId,
            UUID tenantId,
            UUID thirdPartyId,
            Set<TntThirdPartyRole> roles,
            Instant occurredAt
    ) {}

    /**
     * Published when a client profile is deactivated.
     */
    public record ClientProfileDeactivatedEvent(
            UUID profileId,
            UUID tenantId,
            UUID thirdPartyId,
            Instant occurredAt
    ) {}

    // ─── KYC Events ──────────────────────────────────────────────────────────

    /**
     * Published when KYC status changes.
     */
    public record KycStatusChangedEvent(
            UUID kycRecordId,
            UUID tenantId,
            UUID thirdPartyId,
            KycStatus previousStatus,
            KycStatus newStatus,
            String rejectionReason,
            Instant occurredAt
    ) {}

    // ─── Loyalty Events ───────────────────────────────────────────────────────

    /**
     * Published when loyalty points are earned.
     */
    public record LoyaltyPointsEarnedEvent(
            UUID loyaltyAccountId,
            UUID tenantId,
            UUID thirdPartyId,
            int pointsEarned,
            int newBalance,
            String missionId,
            Instant occurredAt
    ) {}

    /**
     * Published when loyalty points are redeemed.
     */
    public record LoyaltyPointsRedeemedEvent(
            UUID loyaltyAccountId,
            UUID tenantId,
            UUID thirdPartyId,
            int pointsRedeemed,
            int newBalance,
            String invoiceId,
            Instant occurredAt
    ) {}

    // ─── Rating Events ────────────────────────────────────────────────────────

    /**
     * Published when a third party is rated.
     */
    public record ThirdPartyRatedEvent(
            UUID ratingId,
            UUID tenantId,
            UUID ratedThirdPartyId,
            String missionId,
            double score,
            double newAverageRating,
            Instant occurredAt
    ) {}

    // ─── Phone Masking Events ─────────────────────────────────────────────────

    /**
     * Published when a phone alias is assigned.
     */
    public record PhoneAliasAssignedEvent(
            UUID profileId,
            UUID tenantId,
            UUID thirdPartyId,
            String phoneAlias,
            Instant occurredAt
    ) {}
}
