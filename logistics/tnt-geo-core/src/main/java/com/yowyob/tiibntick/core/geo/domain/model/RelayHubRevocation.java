package com.yowyob.tiibntick.core.geo.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a relay hub revocation (D-129): the revoked hub and the parcels it still held at that moment.
 *
 * <p>The decision says these parcels are returned or transferred; their fate is NOT decided here: the list is
 * published to the administrator (response) and traced (log), and the return/transfer is to be wired with the
 * parcel lifecycle (events workstream, lot L2).
 *
 * @param hub              the hub, now {@link HubApprovalStatus#REVOKED}
 * @param parcelsInCustody parcels deposited and not picked up (empty when unknown)
 * @param custodyKnown     false when the inventory could not be read (no inventory adapter, or read failure)
 */
public record RelayHubRevocation(RelayHub hub, List<HubCustodyItem> parcelsInCustody, boolean custodyKnown) {

    public RelayHubRevocation {
        Objects.requireNonNull(hub, "hub must not be null");
        parcelsInCustody = parcelsInCustody == null ? List.of() : List.copyOf(parcelsInCustody);
    }
}
