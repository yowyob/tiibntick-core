package com.yowyob.kernel.event.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity for the {@code event_bus.outbox_entries} table.
 *
 * <p>DDL:
 * <pre>{@code
 * CREATE TABLE event_bus.outbox_entries (
 *     id                  VARCHAR(36)  PRIMARY KEY,
 *     envelope_id         VARCHAR(36)  NOT NULL,
 *     tenant_id           VARCHAR(36)  NOT NULL,
 *     kafka_topic         VARCHAR(200) NOT NULL,
 *     kafka_partition_key VARCHAR(100) NOT NULL,
 *     status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
 *     scheduled_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *     processed_at        TIMESTAMPTZ,
 *     processing_attempt  INTEGER      NOT NULL DEFAULT 0,
 *     headers             JSONB        NOT NULL DEFAULT '{}'
 * );
 * CREATE INDEX idx_outbox_status ON event_bus.outbox_entries (status, scheduled_at);
 * }</pre>
 */
@Table(name = "outbox_entries", schema = "event_bus")
public class OutboxEntryEntity {

    @Id
    private String id;

    @Column("envelope_id")
    private String envelopeId;

    @Column("tenant_id")
    private String tenantId;

    @Column("kafka_topic")
    private String kafkaTopic;

    @Column("kafka_partition_key")
    private String kafkaPartitionKey;

    @Column("status")
    private String status;

    @Column("scheduled_at")
    private LocalDateTime scheduledAt;

    @Column("processed_at")
    private LocalDateTime processedAt;

    @Column("processing_attempt")
    private int processingAttempt;

    // Stored as JSONB; read/written as JSON string by the mapper
    @Column("headers")
    private String headersJson;

    public OutboxEntryEntity() {}

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                      { return id; }
    public void setId(String id)               { this.id = id; }

    public String getEnvelopeId()              { return envelopeId; }
    public void setEnvelopeId(String v)        { this.envelopeId = v; }

    public String getTenantId()                { return tenantId; }
    public void setTenantId(String v)          { this.tenantId = v; }

    public String getKafkaTopic()              { return kafkaTopic; }
    public void setKafkaTopic(String v)        { this.kafkaTopic = v; }

    public String getKafkaPartitionKey()       { return kafkaPartitionKey; }
    public void setKafkaPartitionKey(String v) { this.kafkaPartitionKey = v; }

    public String getStatus()                  { return status; }
    public void setStatus(String v)            { this.status = v; }

    public LocalDateTime getScheduledAt()      { return scheduledAt; }
    public void setScheduledAt(LocalDateTime v){ this.scheduledAt = v; }

    public LocalDateTime getProcessedAt()      { return processedAt; }
    public void setProcessedAt(LocalDateTime v){ this.processedAt = v; }

    public int getProcessingAttempt()          { return processingAttempt; }
    public void setProcessingAttempt(int v)    { this.processingAttempt = v; }

    public String getHeadersJson()             { return headersJson; }
    public void setHeadersJson(String v)       { this.headersJson = v; }
}
