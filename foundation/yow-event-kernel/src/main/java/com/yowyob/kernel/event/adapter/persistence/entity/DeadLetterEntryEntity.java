package com.yowyob.kernel.event.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code event_bus.dead_letter_entries} table in
 * {@code yow_kernel_db}.
 *
 * <p>Dead-letter entries are created when a {@link DomainEventEnvelopeEntity}
 * has exhausted all configured retry attempts. Operators use these records to
 * inspect failures, trigger reprocessing or explicitly discard entries via the
 * management API.
 *
 * <p>Table DDL (managed by Liquibase — see {@code 002-create-tables.yaml}):
 * <pre>{@code
 * CREATE TABLE event_bus.dead_letter_entries (
 *     id                  VARCHAR(36) PRIMARY KEY,
 *     original_envelope_id VARCHAR(36) NOT NULL,
 *     kafka_topic          VARCHAR(200) NOT NULL,
 *     original_payload     TEXT NOT NULL,
 *     failure_reason       TEXT,
 *     status               VARCHAR(20) NOT NULL DEFAULT 'WAITING',
 *     failed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *     reprocessed_at       TIMESTAMPTZ,
 *     discard_reason       TEXT
 * );
 * }</pre>
 */
@Table("event_bus.dead_letter_entries")
public class DeadLetterEntryEntity {

    @Id
    private String id;

    @Column("original_envelope_id")
    private String originalEnvelopeId;

    @Column("kafka_topic")
    private String kafkaTopic;

    @Column("original_payload")
    private String originalPayload;

    @Column("failure_reason")
    private String failureReason;

    @Column("status")
    private String status;

    @Column("failed_at")
    private LocalDateTime failedAt;

    @Column("reprocessed_at")
    private LocalDateTime reprocessedAt;

    @Column("discard_reason")
    private String discardReason;

    // ── No-arg constructor required by Spring Data R2DBC ────────────────────

    public DeadLetterEntryEntity() {}

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void   setId(String id)                 { this.id = id; }

    public String getOriginalEnvelopeId()          { return originalEnvelopeId; }
    public void   setOriginalEnvelopeId(String v)  { this.originalEnvelopeId = v; }

    public String getKafkaTopic()                  { return kafkaTopic; }
    public void   setKafkaTopic(String v)          { this.kafkaTopic = v; }

    public String getOriginalPayload()             { return originalPayload; }
    public void   setOriginalPayload(String v)     { this.originalPayload = v; }

    public String getFailureReason()               { return failureReason; }
    public void   setFailureReason(String v)       { this.failureReason = v; }

    public String getStatus()                      { return status; }
    public void   setStatus(String v)              { this.status = v; }

    public LocalDateTime getFailedAt()             { return failedAt; }
    public void setFailedAt(LocalDateTime v)        { this.failedAt = v; }

    public LocalDateTime getReprocessedAt()        { return reprocessedAt; }
    public void setReprocessedAt(LocalDateTime v)  { this.reprocessedAt = v; }

    public String getDiscardReason()               { return discardReason; }
    public void   setDiscardReason(String v)       { this.discardReason = v; }
}
