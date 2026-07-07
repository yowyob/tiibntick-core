package com.yowyob.kernel.event.domain.model;

import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Aggregate root that wraps a domain event for durable, exactly-once delivery
 * through the Yowyob transactional outbox pipeline.
 *
 * <p><strong>Lifecycle</strong>
 * <ol>
 *   <li>A domain service creates an envelope via {@link #wrap} and persists it
 *       atomically alongside the business operation (same DB transaction).</li>
 *   <li>The outbox poller picks up PENDING envelopes and publishes them to Kafka.</li>
 *   <li>On success the envelope transitions to {@link EnvelopeStatus#PUBLISHED}.</li>
 *   <li>On failure the poller calls {@link #markFailed} and schedules a retry.</li>
 *   <li>After exhausting all retries the envelope is moved to the DLQ via
 *       {@link #markDead}.</li>
 * </ol>
 *
 * <p><strong>Immutability</strong><br>
 * Fields that represent identity ({@link #id}, {@link #correlationId},
 * {@link #aggregateId}, {@link #eventType}) are final and set at construction time.
 * Mutable fields ({@link #status}, {@link #retryCount}, etc.) are updated only
 * through explicit state-transition methods.
 */
public class DomainEventEnvelope {

    // ── Identity ────────────────────────────────────────────────────────────

    /** Unique identifier of this envelope (not the underlying event). */
    private final EnvelopeId id;

    /** Business correlation ID propagated across service boundaries. */
    private final String correlationId;

    /**
     * Optional causation ID — the ID of the command or event that caused this
     * event to be emitted, enabling causal ordering in audit logs.
     */
    private final String causationId;

    // ── Event metadata ──────────────────────────────────────────────────────

    /** Fully qualified event type name (e.g. {@code MissionCreatedEvent}). */
    private final String eventType;

    /** Identifier of the aggregate that emitted this event. */
    private final String aggregateId;

    /** Discriminator for the aggregate type (e.g. {@code Mission}). */
    private final String aggregateType;

    /** Multi-tenant isolation: the tenant that owns this event. */
    private final String tenantId;

    /**
     * Yowyob solution code that produced this event (e.g. {@code TNT}, {@code KSM}).
     * Used for cross-solution routing and monitoring.
     */
    private final String solutionCode;

    // ── Payload ─────────────────────────────────────────────────────────────

    /**
     * JSON-serialised event payload.
     * Avro binary encoding is applied at the Kafka adapter layer, keeping the
     * domain model free of serialisation concerns.
     */
    private final String payload;

    /**
     * Avro schema version used for this payload. Stored for replay scenarios
     * where the consumer must deserialise against the original schema.
     */
    private final int schemaVersion;

    /** SHA-256 hex digest of the payload, used for integrity verification. */
    private final String payloadHash;

    // ── Routing ─────────────────────────────────────────────────────────────

    /** Target Kafka topic name. */
    private final String kafkaTopic;

    /**
     * Kafka partition key. Defaults to {@link #aggregateId} to ensure that all
     * events of the same aggregate are delivered in order.
     */
    private final String kafkaPartitionKey;

    // ── Lifecycle ───────────────────────────────────────────────────────────

    private EnvelopeStatus status;
    private int retryCount;
    private String lastError;
    private final LocalDateTime occurredAt;
    private LocalDateTime publishedAt;
    private final RetryPolicy retryPolicy;

    // ── Optimistic locking ──────────────────────────────────────────────────

    private int version;

    // ── Private constructor ──────────────────────────────────────────────────

    private DomainEventEnvelope(final Builder builder) {
        this.id                = Objects.requireNonNull(builder.id, "id");
        this.correlationId     = Objects.requireNonNull(builder.correlationId, "correlationId");
        this.causationId       = builder.causationId;
        this.eventType         = Objects.requireNonNull(builder.eventType, "eventType");
        this.aggregateId       = Objects.requireNonNull(builder.aggregateId, "aggregateId");
        this.aggregateType     = Objects.requireNonNull(builder.aggregateType, "aggregateType");
        this.tenantId          = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.solutionCode      = Objects.requireNonNull(builder.solutionCode, "solutionCode");
        this.payload           = Objects.requireNonNull(builder.payload, "payload");
        this.schemaVersion     = builder.schemaVersion;
        this.payloadHash       = computeHash(builder.payload);
        this.kafkaTopic        = Objects.requireNonNull(builder.kafkaTopic, "kafkaTopic");
        this.kafkaPartitionKey = builder.kafkaPartitionKey != null ? builder.kafkaPartitionKey : builder.aggregateId;
        this.status            = EnvelopeStatus.PENDING;
        this.retryCount        = 0;
        this.occurredAt        = builder.occurredAt != null ? builder.occurredAt : LocalDateTime.now();
        this.retryPolicy       = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.defaultOutboxPolicy();
        this.version           = 0;
    }

    // ── Static factory ───────────────────────────────────────────────────────

    /**
     * Creates a new {@code DomainEventEnvelope} wrapping a domain event.
     *
     * <p>The resulting envelope is in {@link EnvelopeStatus#PENDING} state and
     * must be persisted as part of the current business transaction before the
     * method returns.
     *
     * @return a pre-configured {@link Builder}
     */
    public static Builder wrap() {
        return new Builder();
    }

    /**
     * Reconstructs a {@code DomainEventEnvelope} from persisted data without
     * going through the state-machine transition methods.
     *
     * <p>This factory bypasses invariant checks that would otherwise prevent
     * direct assignment of persisted lifecycle fields (status, retryCount, etc.).
     * It is intended <strong>exclusively</strong> for the persistence adapter layer.
     * Application code must use {@link #wrap()} instead.
     *
     * @param id              persisted envelope identifier
     * @param correlationId   persisted correlation ID
     * @param causationId     persisted causation ID (may be {@code null})
     * @param eventType       fully qualified event type name
     * @param aggregateId     aggregate identifier
     * @param aggregateType   aggregate type discriminator
     * @param tenantId        multi-tenant isolation key
     * @param solutionCode    originating solution code
     * @param payload         JSON-serialised payload (hash recomputed on restore)
     * @param schemaVersion   Avro schema version
     * @param kafkaTopic      Kafka topic name
     * @param kafkaPartitionKey Kafka partition key
     * @param status          persisted lifecycle status
     * @param retryCount      number of failed publish attempts
     * @param lastError       last recorded error message (may be {@code null})
     * @param occurredAt      event occurrence timestamp
     * @param publishedAt     successful publication timestamp (may be {@code null})
     * @param version         optimistic lock version
     * @param retryPolicy     retry configuration (uses default if {@code null})
     * @return fully-restored domain aggregate
     */
    public static DomainEventEnvelope restore(
            final EnvelopeId id,
            final String correlationId,
            final String causationId,
            final String eventType,
            final String aggregateId,
            final String aggregateType,
            final String tenantId,
            final String solutionCode,
            final String payload,
            final int schemaVersion,
            final String kafkaTopic,
            final String kafkaPartitionKey,
            final EnvelopeStatus status,
            final int retryCount,
            final String lastError,
            final LocalDateTime occurredAt,
            final LocalDateTime publishedAt,
            final int version,
            final RetryPolicy retryPolicy) {

        DomainEventEnvelope env = DomainEventEnvelope.wrap()
            .id(id)
            .correlationId(correlationId)
            .causationId(causationId)
            .eventType(eventType)
            .aggregateId(aggregateId)
            .aggregateType(aggregateType)
            .tenantId(tenantId)
            .solutionCode(solutionCode)
            .payload(payload)
            .schemaVersion(schemaVersion)
            .kafkaTopic(kafkaTopic)
            .kafkaPartitionKey(kafkaPartitionKey)
            .occurredAt(occurredAt)
            .retryPolicy(retryPolicy != null ? retryPolicy : RetryPolicy.defaultOutboxPolicy())
            .build();

        // Directly set mutable persisted state without going through the state machine.
        // This is the only legitimate use of direct field assignment outside the constructor.
        env.status      = status != null ? status : EnvelopeStatus.PENDING;
        env.retryCount  = retryCount;
        env.lastError   = lastError;
        env.publishedAt = publishedAt;
        env.version     = version;
        return env;
    }

    // ── State transitions ────────────────────────────────────────────────────

    /**
     * Marks this envelope as successfully published.
     *
     * <p>Called by the outbox poller after Kafka acknowledges the write.
     *
     * @throws IllegalStateException if the envelope is not in a publishable state
     */
    public void markPublished() {
        if (status != EnvelopeStatus.PENDING && status != EnvelopeStatus.RETRYING) {
            throw new IllegalStateException(
                "Cannot mark envelope as PUBLISHED from status " + status);
        }
        this.status      = EnvelopeStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError   = null;
        this.version++;
    }

    /**
     * Records a publishing failure and increments the retry counter.
     *
     * <p>If {@link RetryPolicy#hasExceededMaxAttempts} returns {@code true}, the
     * envelope is moved directly to {@link EnvelopeStatus#DEAD} rather than
     * {@link EnvelopeStatus#FAILED}.
     *
     * @param error human-readable description of the failure cause
     * @throws IllegalStateException if the envelope is already PUBLISHED or DEAD
     */
    public void markFailed(final String error) {
        if (status == EnvelopeStatus.PUBLISHED || status == EnvelopeStatus.DEAD) {
            throw new IllegalStateException(
                "Cannot mark envelope as FAILED from status " + status);
        }
        this.retryCount++;
        this.lastError = error;
        if (retryPolicy.hasExceededMaxAttempts(retryCount)) {
            this.status = EnvelopeStatus.DEAD;
        } else {
            this.status = EnvelopeStatus.FAILED;
        }
        this.version++;
    }

    /**
     * Schedules the next retry attempt by transitioning to
     * {@link EnvelopeStatus#RETRYING}.
     *
     * @throws IllegalStateException if the envelope is not in FAILED state
     */
    public void scheduleRetry() {
        if (status != EnvelopeStatus.FAILED) {
            throw new IllegalStateException(
                "Can only schedule retry from FAILED status, current: " + status);
        }
        this.status = EnvelopeStatus.RETRYING;
        this.version++;
    }

    /**
     * Forces this envelope into the DLQ, bypassing remaining retry attempts.
     * Typically called by an administrator via the management API.
     *
     * @param reason the administrative reason for forcing the DLQ transition
     */
    public void markDead(final String reason) {
        this.status    = EnvelopeStatus.DEAD;
        this.lastError = reason;
        this.version++;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when no further retry attempts should be made.
     *
     * @return {@code true} if the envelope is in the {@link EnvelopeStatus#DEAD} state
     */
    public boolean isDead() {
        return status == EnvelopeStatus.DEAD;
    }

    /**
     * Returns {@code true} when the payload can still be safely retried.
     *
     * @return {@code true} if status is PENDING, FAILED, or RETRYING
     */
    public boolean isRetryable() {
        return status == EnvelopeStatus.PENDING
            || status == EnvelopeStatus.FAILED
            || status == EnvelopeStatus.RETRYING;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static String computeHash(final String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public EnvelopeId getId()               { return id; }
    public String getCorrelationId()        { return correlationId; }
    public String getCausationId()          { return causationId; }
    public String getEventType()            { return eventType; }
    public String getAggregateId()          { return aggregateId; }
    public String getAggregateType()        { return aggregateType; }
    public String getTenantId()             { return tenantId; }
    public String getSolutionCode()         { return solutionCode; }
    public String getPayload()              { return payload; }
    public int    getSchemaVersion()        { return schemaVersion; }
    public String getPayloadHash()          { return payloadHash; }
    public String getKafkaTopic()           { return kafkaTopic; }
    public String getKafkaPartitionKey()    { return kafkaPartitionKey; }
    public EnvelopeStatus getStatus()       { return status; }
    public int    getRetryCount()           { return retryCount; }
    public String getLastError()            { return lastError; }
    public LocalDateTime getOccurredAt()    { return occurredAt; }
    public LocalDateTime getPublishedAt()   { return publishedAt; }
    public RetryPolicy getRetryPolicy()     { return retryPolicy; }
    public int    getVersion()              { return version; }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link DomainEventEnvelope}.
     *
     * <p>All required fields must be set before calling {@link #build()}.
     */
    public static final class Builder {

        private EnvelopeId id = EnvelopeId.generate();
        private String correlationId;
        private String causationId;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private String tenantId;
        private String solutionCode;
        private String payload;
        private int    schemaVersion = 1;
        private String kafkaTopic;
        private String kafkaPartitionKey;
        private LocalDateTime occurredAt;
        private RetryPolicy retryPolicy;

        private Builder() {}

        public Builder id(final EnvelopeId id)                      { this.id = id; return this; }
        public Builder correlationId(final String correlationId)     { this.correlationId = correlationId; return this; }
        public Builder causationId(final String causationId)         { this.causationId = causationId; return this; }
        public Builder eventType(final String eventType)             { this.eventType = eventType; return this; }
        public Builder aggregateId(final String aggregateId)         { this.aggregateId = aggregateId; return this; }
        public Builder aggregateType(final String aggregateType)     { this.aggregateType = aggregateType; return this; }
        public Builder tenantId(final String tenantId)               { this.tenantId = tenantId; return this; }
        public Builder solutionCode(final String solutionCode)       { this.solutionCode = solutionCode; return this; }
        public Builder payload(final String payload)                  { this.payload = payload; return this; }
        public Builder schemaVersion(final int schemaVersion)        { this.schemaVersion = schemaVersion; return this; }
        public Builder kafkaTopic(final String kafkaTopic)           { this.kafkaTopic = kafkaTopic; return this; }
        public Builder kafkaPartitionKey(final String key)           { this.kafkaPartitionKey = key; return this; }
        public Builder occurredAt(final LocalDateTime occurredAt)    { this.occurredAt = occurredAt; return this; }
        public Builder retryPolicy(final RetryPolicy retryPolicy)    { this.retryPolicy = retryPolicy; return this; }

        /**
         * Constructs the {@link DomainEventEnvelope}.
         *
         * @return a new, immutable envelope in {@link EnvelopeStatus#PENDING} state
         * @throws NullPointerException if any required field is absent
         */
        public DomainEventEnvelope build() {
            return new DomainEventEnvelope(this);
        }
    }
}
