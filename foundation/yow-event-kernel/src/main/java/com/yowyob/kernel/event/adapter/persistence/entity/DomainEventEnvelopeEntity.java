package com.yowyob.kernel.event.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code trust_events_log} table in
 * {@code yow_kernel_db} schema {@code event_bus}.
 *
 * <p>This entity mirrors the {@link com.yowyob.kernel.event.domain.model.DomainEventEnvelope}
 * aggregate but is intentionally kept separate to avoid polluting the domain
 * model with persistence annotations (hexagonal architecture principle).
 *
 * <p>Table DDL (managed by Liquibase):
 * <pre>{@code
 * CREATE TABLE event_bus.domain_event_envelopes (
 *     id                  VARCHAR(36)  PRIMARY KEY,
 *     correlation_id      VARCHAR(36)  NOT NULL,
 *     causation_id        VARCHAR(36),
 *     event_type          VARCHAR(200) NOT NULL,
 *     aggregate_id        VARCHAR(36)  NOT NULL,
 *     aggregate_type      VARCHAR(100) NOT NULL,
 *     tenant_id           VARCHAR(36)  NOT NULL,
 *     solution_code       VARCHAR(10)  NOT NULL,
 *     payload             TEXT         NOT NULL,
 *     schema_version      INTEGER      NOT NULL DEFAULT 1,
 *     payload_hash        VARCHAR(64)  NOT NULL,
 *     kafka_topic         VARCHAR(200) NOT NULL,
 *     kafka_partition_key VARCHAR(100) NOT NULL,
 *     status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
 *     retry_count         INTEGER      NOT NULL DEFAULT 0,
 *     last_error          TEXT,
 *     occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *     published_at        TIMESTAMPTZ,
 *     version             INTEGER      NOT NULL DEFAULT 0
 * );
 * CREATE INDEX idx_dee_aggregate  ON event_bus.domain_event_envelopes (aggregate_id, aggregate_type, tenant_id);
 * CREATE INDEX idx_dee_event_type ON event_bus.domain_event_envelopes (event_type, tenant_id, occurred_at);
 * CREATE INDEX idx_dee_status     ON event_bus.domain_event_envelopes (status, tenant_id);
 * CREATE INDEX idx_dee_correlation ON event_bus.domain_event_envelopes (correlation_id, tenant_id);
 * }</pre>
 */
@Table("event_bus.domain_event_envelopes")
public class DomainEventEnvelopeEntity {

    @Id
    private String id;

    @Column("correlation_id")
    private String correlationId;

    @Column("causation_id")
    private String causationId;

    @Column("event_type")
    private String eventType;

    @Column("aggregate_id")
    private String aggregateId;

    @Column("aggregate_type")
    private String aggregateType;

    @Column("tenant_id")
    private String tenantId;

    @Column("solution_code")
    private String solutionCode;

    @Column("payload")
    private String payload;

    @Column("schema_version")
    private int schemaVersion;

    @Column("payload_hash")
    private String payloadHash;

    @Column("kafka_topic")
    private String kafkaTopic;

    @Column("kafka_partition_key")
    private String kafkaPartitionKey;

    @Column("status")
    private String status;

    @Column("retry_count")
    private int retryCount;

    @Column("last_error")
    private String lastError;

    @Column("occurred_at")
    private LocalDateTime occurredAt;

    @Column("published_at")
    private LocalDateTime publishedAt;

    @Version
    @Column("version")
    private int version;

    // ── No-arg constructor required by Spring Data R2DBC ────────────────────

    public DomainEventEnvelopeEntity() {}

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getId()                     { return id; }
    public void setId(String id)              { this.id = id; }

    public String getCorrelationId()          { return correlationId; }
    public void setCorrelationId(String v)    { this.correlationId = v; }

    public String getCausationId()            { return causationId; }
    public void setCausationId(String v)      { this.causationId = v; }

    public String getEventType()              { return eventType; }
    public void setEventType(String v)        { this.eventType = v; }

    public String getAggregateId()            { return aggregateId; }
    public void setAggregateId(String v)      { this.aggregateId = v; }

    public String getAggregateType()          { return aggregateType; }
    public void setAggregateType(String v)    { this.aggregateType = v; }

    public String getTenantId()               { return tenantId; }
    public void setTenantId(String v)         { this.tenantId = v; }

    public String getSolutionCode()           { return solutionCode; }
    public void setSolutionCode(String v)     { this.solutionCode = v; }

    public String getPayload()                { return payload; }
    public void setPayload(String v)          { this.payload = v; }

    public int getSchemaVersion()             { return schemaVersion; }
    public void setSchemaVersion(int v)       { this.schemaVersion = v; }

    public String getPayloadHash()            { return payloadHash; }
    public void setPayloadHash(String v)      { this.payloadHash = v; }

    public String getKafkaTopic()             { return kafkaTopic; }
    public void setKafkaTopic(String v)       { this.kafkaTopic = v; }

    public String getKafkaPartitionKey()      { return kafkaPartitionKey; }
    public void setKafkaPartitionKey(String v){ this.kafkaPartitionKey = v; }

    public String getStatus()                 { return status; }
    public void setStatus(String v)           { this.status = v; }

    public int getRetryCount()                { return retryCount; }
    public void setRetryCount(int v)          { this.retryCount = v; }

    public String getLastError()              { return lastError; }
    public void setLastError(String v)        { this.lastError = v; }

    public LocalDateTime getOccurredAt()      { return occurredAt; }
    public void setOccurredAt(LocalDateTime v){ this.occurredAt = v; }

    public LocalDateTime getPublishedAt()     { return publishedAt; }
    public void setPublishedAt(LocalDateTime v){ this.publishedAt = v; }

    public int getVersion()                   { return version; }
    public void setVersion(int v)             { this.version = v; }
}
