package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/**
 * Response DTO for a single block in an incident's blockchain audit chain.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentBlockchainRecordResponse {
    UUID id;
    UUID incidentId;
    String chainId;
    long blockIndex;
    String previousHash;
    String currentHash;
    String eventType;
    String payload;
    Instant createdAt;
    long nonce;
    boolean verified;
}
