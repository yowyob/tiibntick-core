package com.yowyob.tiibntick.core.organization.domain.event;

import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a FreelancerOrganization's KYC level is upgraded.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code tnt-administration-core} — triggers admin review if needed.</li>
 *   <li>{@code tnt-notify-core}         — notifies the OWNER of the level change.</li>
 * </ul>
 *
 * @param orgId        TiiBnTick internal FreelancerOrganization UUID
 * @param tenantId     Multi-tenant key
 * @param ownerActorId OWNER actor UUID
 * @param previousLevel KYC level before the upgrade
 * @param newLevel     KYC level after the upgrade
 * @param occurredAt   Event timestamp (UTC)
 *
 * @author MANFOUO Braun
 */
public record KycLevelUpgradedEvent(
        UUID orgId,
        String tenantId,
        UUID ownerActorId,
        KycLevel previousLevel,
        KycLevel newLevel,
        Instant occurredAt
) {
    public static KycLevelUpgradedEvent of(UUID orgId, String tenantId,
                                            UUID ownerActorId,
                                            KycLevel previousLevel, KycLevel newLevel) {
        return new KycLevelUpgradedEvent(orgId, tenantId, ownerActorId,
                previousLevel, newLevel, Instant.now());
    }
}
