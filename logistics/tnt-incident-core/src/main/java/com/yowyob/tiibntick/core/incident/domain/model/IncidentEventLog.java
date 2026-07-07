package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ordered log entry recording every significant action on an incident, with optional blockchain proof.
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
public class IncidentEventLog {

    private UUID id;
    private UUID incidentId;
    private String eventType;
    private Instant occurredAt;
    private UUID performedByActorId;
    private ActorRole performedByRole;
    private String payload;
    private String blockchainTxHash;
    private String blockchainChainRef;
    private boolean writtenOnParcelChain;
    private boolean writtenOnIncidentChain;

    public static IncidentEventLog of(UUID incidentId, String eventType,
                                      UUID actorId, ActorRole role, String payload) {
        return IncidentEventLog.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .eventType(eventType)
                .occurredAt(Instant.now())
                .performedByActorId(actorId)
                .performedByRole(role)
                .payload(payload)
                .writtenOnParcelChain(false)
                .writtenOnIncidentChain(false)
                .build();
    }

    public IncidentEventLog withBlockchainProof(String txHash, String chainRef,
                                                boolean onParcel, boolean onIncident) {
        return toBuilder()
                .blockchainTxHash(txHash)
                .blockchainChainRef(chainRef)
                .writtenOnParcelChain(onParcel)
                .writtenOnIncidentChain(onIncident)
                .build();
    }

    public IncidentEventLogBuilder toBuilder() {
        return IncidentEventLog.builder().id(id).incidentId(incidentId).eventType(eventType)
                .occurredAt(occurredAt).performedByActorId(performedByActorId)
                .performedByRole(performedByRole).payload(payload)
                .blockchainTxHash(blockchainTxHash).blockchainChainRef(blockchainChainRef)
                .writtenOnParcelChain(writtenOnParcelChain).writtenOnIncidentChain(writtenOnIncidentChain);
    }
}
