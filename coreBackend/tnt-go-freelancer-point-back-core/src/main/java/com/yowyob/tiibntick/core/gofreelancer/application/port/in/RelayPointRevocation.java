package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of the revocation of a Go-Freelancer relay point (decision D-129).
 *
 * @param relayPointId     the Go relay point
 * @param hubId            its core relay hub ({@code null} when it has none yet)
 * @param status           always {@code REVOKED}
 * @param reason           the administrator's reason
 * @param revokedAt        date of the revocation
 * @param coreHubRevoked   true when the core hub was revoked (false: relay point without core hub, revoked locally)
 * @param custodyKnown     true when the parcels in custody could be read from the core inventory
 * @param parcelsInCustody parcels still held by the relay (to return or transfer: not wired yet, events lot L2)
 */
public record RelayPointRevocation(UUID relayPointId, UUID hubId, String status, String reason, Instant revokedAt,
                                   boolean coreHubRevoked, boolean custodyKnown,
                                   List<HubCustodyItem> parcelsInCustody) {
}
