package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC Entity — {@code IncidentBlockchainRecordEntity}.
 * Maps to the {@code tnt_trust.incident_blockchain_records} table.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "incident_blockchain_records")
public class IncidentBlockchainRecordEntity {

    /** Primary key — auto-generated UUID. */
    @Id
    @Column("record_id")
    private UUID recordId;

    /** Chain identifier — format: INC-{uuid} for incident chains. */
    @Column("chain_id")
    private String chainId;

    /** Sequential block index within the chain (0 = genesis). */
    @Column("block_index")
    private long blockIndex;

    /** SHA-256 hash of the previous block (GENESIS for index 0). */
    @Column("previous_hash")
    private String previousHash;

    /** SHA-256 hash of this block (current). */
    @Column("current_hash")
    private String currentHash;

    /** Event type anchored in this block (matches LogisticTrustEventType). */
    @Column("event_type")
    private String eventType;

    /** JSON payload of the event anchored in this block. */
    @Column("payload")
    private String payload;

    /** Timestamp nonce used in hash computation. */
    @Column("nonce")
    private long nonce;

    /** UTC timestamp when this block was created. */
    @Column("created_at")
    private LocalDateTime createdAt;

    /** The incident UUID this block belongs to. Nullable for non-incident chains. */
    @Column("incident_id")
    private UUID incidentId;

    IncidentBlockchainRecordEntity() {}

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new entity from its constituent fields.
     *
     * @param chainId      the chain identifier
     * @param blockIndex   sequential index within the chain
     * @param previousHash hash of the previous block
     * @param currentHash  hash of this block
     * @param eventType    event type anchored in this block
     * @param payload      JSON payload of the event
     * @param nonce        timestamp nonce used in hash computation
     * @param incidentId   the incident this block belongs to
     * @return a new entity ready for persistence
     */
    static IncidentBlockchainRecordEntity create(
            final String chainId,
            final long blockIndex,
            final String previousHash,
            final String currentHash,
            final String eventType,
            final String payload,
            final long nonce,
            final UUID incidentId) {
        final IncidentBlockchainRecordEntity e = new IncidentBlockchainRecordEntity();
        e.recordId = UUID.randomUUID();
        e.chainId = chainId;
        e.blockIndex = blockIndex;
        e.previousHash = previousHash;
        e.currentHash = currentHash;
        e.eventType = eventType;
        e.payload = payload;
        e.nonce = nonce;
        e.createdAt = LocalDateTime.now();
        e.incidentId = incidentId;
        return e;
    }

    // ── Getters & Setters (required by R2DBC) ────────────────────────────────

    public UUID getRecordId() { return recordId; }
    public void setRecordId(final UUID v) { this.recordId = v; }
    public String getChainId() { return chainId; }
    public void setChainId(final String v) { this.chainId = v; }
    public long getBlockIndex() { return blockIndex; }
    public void setBlockIndex(final long v) { this.blockIndex = v; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(final String v) { this.previousHash = v; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(final String v) { this.currentHash = v; }
    public String getEventType() { return eventType; }
    public void setEventType(final String v) { this.eventType = v; }
    public String getPayload() { return payload; }
    public void setPayload(final String v) { this.payload = v; }
    public long getNonce() { return nonce; }
    public void setNonce(final long v) { this.nonce = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final LocalDateTime v) { this.createdAt = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(final UUID v) { this.incidentId = v; }
}
