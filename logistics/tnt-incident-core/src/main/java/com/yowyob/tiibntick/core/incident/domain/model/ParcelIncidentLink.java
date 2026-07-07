package com.yowyob.tiibntick.core.incident.domain.model;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Blockchain bridge linking a parcel chain to the incident chain and back after resolution.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParcelIncidentLink {

    private UUID parcelId;
    private UUID incidentId;
    private String parcelChainId;
    private String parcelChainTailHash;
    private String incidentChainId;
    private String incidentChainHeadHash;
    private String incidentChainTailHash;
    private Instant linkedAt;
    private Instant resumedAt;
    private boolean resumptionConfirmed;

    /**
     * Creates a link bridging a parcel's blockchain chain tail to the incident chain head.
     * This is the first half of the dual-chain bridge protocol.
     *
     * @param parcelId               the parcel being linked
     * @param incidentId             the incident chain
     * @param parcelChainId          the parcel's chain identifier
     * @param parcelChainTailHash    the last hash in the parcel's chain before the incident
     * @param incidentChainId        the incident's dedicated chain identifier
     * @param incidentChainHeadHash  the first hash of the incident chain
     * @return the established link record
     */
    public static ParcelIncidentLink link(UUID parcelId, UUID incidentId,
                                          String parcelChainId, String parcelChainTailHash,
                                          String incidentChainId, String incidentChainHeadHash) {
        return ParcelIncidentLink.builder()
                .parcelId(parcelId)
                .incidentId(incidentId)
                .parcelChainId(parcelChainId)
                .parcelChainTailHash(parcelChainTailHash)
                .incidentChainId(incidentChainId)
                .incidentChainHeadHash(incidentChainHeadHash)
                .linkedAt(Instant.now())
                .resumptionConfirmed(false)
                .build();
    }

    /**
     * Records the end of the incident chain being linked back to the parcel chain.
     * This completes the dual-chain bridge protocol.
     *
     * @param incidentChainTailHash the final hash of the incident chain at resolution
     * @return the link record with resumption confirmed
     */
    public ParcelIncidentLink resume(String incidentChainTailHash) {
        return toBuilder()
                .incidentChainTailHash(incidentChainTailHash)
                .resumedAt(Instant.now())
                .resumptionConfirmed(true)
                .build();
    }

    public ParcelIncidentLinkBuilder toBuilder() {
        return ParcelIncidentLink.builder().parcelId(parcelId).incidentId(incidentId)
                .parcelChainId(parcelChainId).parcelChainTailHash(parcelChainTailHash)
                .incidentChainId(incidentChainId).incidentChainHeadHash(incidentChainHeadHash)
                .incidentChainTailHash(incidentChainTailHash).linkedAt(linkedAt)
                .resumedAt(resumedAt).resumptionConfirmed(resumptionConfirmed);
    }
}
