package com.yowyob.tiibntick.core.incident.domain.model;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Single block in the incident-specific blockchain chain, SHA-256 linked to the previous block.
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
public class IncidentBlockchainRecord {

    private UUID id;
    private UUID incidentId;
    private String chainId;
    private long blockIndex;
    private String previousHash;
    private String currentHash;
    private String eventType;
    private String payload;
    private Instant createdAt;
    private long nonce;
    private boolean verified;

    /**
     * Creates a new immutable blockchain block.
     *
     * @param incidentId    the incident owning this chain
     * @param chainId       the chain identifier
     * @param blockIndex    sequential position in the chain
     * @param previousHash  SHA-256 hash of the preceding block
     * @param currentHash   SHA-256 hash of this block
     * @param eventType     the type of event being recorded
     * @param payload       JSON event payload
     * @param nonce         anti-collision nonce
     * @return the new blockchain record
     */
    public static IncidentBlockchainRecord create(UUID incidentId, String chainId,
                                                   long blockIndex, String previousHash,
                                                   String currentHash, String eventType,
                                                   String payload, long nonce) {
        return IncidentBlockchainRecord.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .chainId(chainId)
                .blockIndex(blockIndex)
                .previousHash(previousHash)
                .currentHash(currentHash)
                .eventType(eventType)
                .payload(payload)
                .createdAt(Instant.now())
                .nonce(nonce)
                .verified(false)
                .build();
    }

    public IncidentBlockchainRecord markVerified() {
        return toBuilder().verified(true).build();
    }

    public IncidentBlockchainRecordBuilder toBuilder() {
        return IncidentBlockchainRecord.builder().id(id).incidentId(incidentId)
                .chainId(chainId).blockIndex(blockIndex).previousHash(previousHash)
                .currentHash(currentHash).eventType(eventType).payload(payload)
                .createdAt(createdAt).nonce(nonce).verified(verified);
    }
}
