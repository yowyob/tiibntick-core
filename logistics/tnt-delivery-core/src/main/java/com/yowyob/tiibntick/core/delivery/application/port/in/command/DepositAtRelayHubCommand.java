package com.yowyob.tiibntick.core.delivery.application.port.in.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Row D7: deposit of the parcel at a relay hub.
 *
 * @param relayHubId           relay hub (geo-core hub id)
 * @param hubPackageEntryId    inventory entry created beforehand by the relay flow, may be {@code null}
 * @param requestedStorageDays storage asked by the client (QO-30: effective = min(request, relay)), may be {@code null}
 */
public record DepositAtRelayHubCommand(
        @NotNull UUID tenantId,
        @NotNull UUID deliveryId,
        @NotNull UUID deliveryPersonId,
        @NotNull UUID relayHubId,
        UUID hubPackageEntryId,
        Integer requestedStorageDays
) {
    public DepositAtRelayHubCommand(UUID tenantId, UUID deliveryId, UUID deliveryPersonId, UUID relayHubId) {
        this(tenantId, deliveryId, deliveryPersonId, relayHubId, null, null);
    }
}
