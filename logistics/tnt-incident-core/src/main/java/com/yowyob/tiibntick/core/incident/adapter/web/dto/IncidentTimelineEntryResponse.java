package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/**
 * Response DTO for a single incident timeline entry.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentTimelineEntryResponse {
    UUID id;
    UUID incidentId;
    String eventType;
    Instant occurredAt;
    UUID performedByActorId;
    ActorRole performedByRole;
    String payload;
    String blockchainTxHash;
    String blockchainChainRef;
    boolean writtenOnParcelChain;
    boolean writtenOnIncidentChain;
}
