package com.yowyob.tiibntick.core.tp.domain.event;

import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Domain event emitted when a new TntClientProfile is registered.
 *
 * @author MANFOUO Braun
 */
public record ClientProfileRegisteredEvent(
        UUID profileId,
        UUID tenantId,
        UUID thirdPartyId,
        Set<TntThirdPartyRole> roles,
        Instant occurredAt
) {}
