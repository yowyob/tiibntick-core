package com.yowyob.kernel.event.domain.model;

import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.OutboxEntryId;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single entry in the transactional outbox table.
 *
 * <p>An {@code OutboxEntry} is created alongside a {@link DomainEventEnvelope}
 * within the same database transaction as the triggering business operation.
 * The outbox poller then reads pending entries and delivers them to Kafka,
 * guaranteeing at-least-once delivery even in the presence of infrastructure
 * failures.
 *
 * <p>Unlike {@link DomainEventEnvelope}, which carries the full event payload
 * for audit and replay, {@code OutboxEntry} is a lightweight routing record
 * whose sole purpose is to drive Kafka delivery. It is deleted (or archived)
 * once processing succeeds.
 */
public class OutboxEntry {

    private final OutboxEntryId id;
    private final EnvelopeId    envelopeId;
    private final String        tenantId;
    private final String        kafkaTopic;
    private final String        kafkaPartitionKey;
    private OutboxStatus        status;
    private final LocalDateTime scheduledAt;
    private LocalDateTime       processedAt;
    private int                 processingAttempt;
    private final Map<String, String> headers;

    // ── Constructor ──────────────────────────────────────────────────────────

    private OutboxEntry(
            final OutboxEntryId id,
            final EnvelopeId envelopeId,
            final String tenantId,
            final String kafkaTopic,
            final String kafkaPartitionKey,
            final Map<String, String> headers) {
        this.id                = Objects.requireNonNull(id);
        this.envelopeId        = Objects.requireNonNull(envelopeId);
        this.tenantId          = tenantId;
        this.kafkaTopic        = Objects.requireNonNull(kafkaTopic);
        this.kafkaPartitionKey = Objects.requireNonNull(kafkaPartitionKey);
        this.headers           = headers != null ? Map.copyOf(headers) : Map.of();
        this.status            = OutboxStatus.PENDING;
        this.scheduledAt       = LocalDateTime.now();
        this.processingAttempt = 0;
    }

    /**
     * Full-state constructor used exclusively for reconstruction from persistence.
     * All fields — including mutable lifecycle fields — are set directly without
     * going through the state-machine transition methods.
     */
    private OutboxEntry(
            final OutboxEntryId id,
            final EnvelopeId envelopeId,
            final String tenantId,
            final String kafkaTopic,
            final String kafkaPartitionKey,
            final OutboxStatus status,
            final LocalDateTime scheduledAt,
            final LocalDateTime processedAt,
            final int processingAttempt,
            final Map<String, String> headers) {
        this.id                = Objects.requireNonNull(id);
        this.envelopeId        = Objects.requireNonNull(envelopeId);
        this.tenantId          = tenantId;
        this.kafkaTopic        = Objects.requireNonNull(kafkaTopic);
        this.kafkaPartitionKey = Objects.requireNonNull(kafkaPartitionKey);
        this.status            = status != null ? status : OutboxStatus.PENDING;
        this.scheduledAt       = scheduledAt != null ? scheduledAt : LocalDateTime.now();
        this.processedAt       = processedAt;
        this.processingAttempt = processingAttempt;
        this.headers           = headers != null ? Map.copyOf(headers) : Map.of();
    }

    // ── Static factory ───────────────────────────────────────────────────────

    /**
     * Creates a new outbox entry for the given envelope.
     *
     * @param envelope the envelope this entry will route
     * @param headers  additional Kafka message headers (may be {@code null})
     * @return a PENDING outbox entry
     */
    public static OutboxEntry forEnvelope(
            final DomainEventEnvelope envelope,
            final Map<String, String> headers) {
        return new OutboxEntry(
            OutboxEntryId.generate(),
            envelope.getId(),
            envelope.getTenantId(),
            envelope.getKafkaTopic(),
            envelope.getKafkaPartitionKey(),
            headers
        );
    }

    /**
     * Reconstructs an {@code OutboxEntry} from persisted data without going through
     * the state-machine transition methods.
     *
     * <p>This method is intended exclusively for the persistence adapter layer.
     * Application code must use {@link #forEnvelope} instead.
     *
     * @param id                unique identifier persisted in the database
     * @param envelopeId        identifier of the associated envelope
     * @param tenantId          multi-tenant isolation key
     * @param kafkaTopic        persisted Kafka topic name
     * @param kafkaPartitionKey persisted partition key
     * @param status            persisted lifecycle status
     * @param scheduledAt       persisted creation timestamp
     * @param processedAt       persisted processing timestamp (may be {@code null})
     * @param processingAttempt persisted attempt counter
     * @param headers           persisted Kafka headers (may be {@code null})
     * @return fully-restored {@code OutboxEntry}
     */
    public static OutboxEntry restore(
            final OutboxEntryId id,
            final EnvelopeId envelopeId,
            final String tenantId,
            final String kafkaTopic,
            final String kafkaPartitionKey,
            final OutboxStatus status,
            final LocalDateTime scheduledAt,
            final LocalDateTime processedAt,
            final int processingAttempt,
            final Map<String, String> headers) {
        return new OutboxEntry(
            id, envelopeId, tenantId, kafkaTopic, kafkaPartitionKey,
            status, scheduledAt, processedAt, processingAttempt, headers
        );
    }

    // ── State transitions ────────────────────────────────────────────────────

    /** Transitions the entry to PROCESSING and increments the attempt counter. */
    public void process() {
        this.status            = OutboxStatus.PROCESSING;
        this.processingAttempt++;
    }

    /** Marks the entry as successfully delivered to Kafka. */
    public void succeed() {
        this.status      = OutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    /** Records a delivery failure; the entry will be retried by the poller. */
    public void fail() {
        this.status = OutboxStatus.FAILED;
    }

    /** Schedules the next retry attempt. */
    public void retry() {
        this.status = OutboxStatus.RETRYING;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public OutboxEntryId getId()                { return id; }
    public EnvelopeId    getEnvelopeId()        { return envelopeId; }
    public String        getTenantId()          { return tenantId; }
    public String        getKafkaTopic()        { return kafkaTopic; }
    public String        getKafkaPartitionKey() { return kafkaPartitionKey; }
    public OutboxStatus  getStatus()            { return status; }
    public LocalDateTime getScheduledAt()       { return scheduledAt; }
    public LocalDateTime getProcessedAt()       { return processedAt; }
    public int           getProcessingAttempt() { return processingAttempt; }
    public Map<String, String> getHeaders()     { return headers; }
}
