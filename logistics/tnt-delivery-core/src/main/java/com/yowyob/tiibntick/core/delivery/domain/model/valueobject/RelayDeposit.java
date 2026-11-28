package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Deposit of a parcel at a relay hub (row D7): the inventory entry created beforehand by the relay flow
 * ({@code RelayDepositService}), the deposit time and the storage deadline computed from the thresholds
 * (QO-30: effective storage = min(client request, relay storage)).
 *
 * @param hubId                 relay hub
 * @param hubPackageEntryId     inventory entry of the deposit, may be {@code null} for a flow without inventory
 * @param depositedAt           deposit time (start of storage)
 * @param destination           whether the hub is the destination of the delivery (storage and D12 apply)
 * @param requestedStorageDays  storage asked by the client, may be {@code null}
 * @param relayStorageDays      effective storage days granted
 * @param storageExpiresAt      end of storage ({@code null} when not a destination)
 * @param reminderAt            when the reminder is due ({@code null} when not a destination)
 */
public record RelayDeposit(UUID hubId, UUID hubPackageEntryId, Instant depositedAt, boolean destination,
                           Integer requestedStorageDays, Integer relayStorageDays, Instant storageExpiresAt,
                           Instant reminderAt) {

    public RelayDeposit {
        Objects.requireNonNull(hubId, "hubId");
        depositedAt = depositedAt == null ? Instant.now() : depositedAt;
    }

    /** An intermediate stop, or a deposit whose storage is not computed (no deadline). */
    public static RelayDeposit at(UUID hubId) {
        return new RelayDeposit(hubId, null, Instant.now(), false, null, null, null, null);
    }
}
