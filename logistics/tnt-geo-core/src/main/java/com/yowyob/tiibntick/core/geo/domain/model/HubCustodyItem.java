package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A parcel currently held by a relay hub (deposited, not picked up), as reported by the module that owns the hub
 * inventory. Read-only view used when a hub is revoked (D-129) to list the parcels that must leave it.
 *
 * @param entryId      id of the inventory entry
 * @param packageId    id of the core parcel
 * @param trackingCode tracking code of the entry
 * @param ownerActorId actor who collects the parcel, when known
 * @param depositedAt  deposit date
 */
public record HubCustodyItem(UUID entryId, UUID packageId, String trackingCode, UUID ownerActorId,
                             Instant depositedAt) {
}
