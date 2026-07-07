package com.yowyob.kernel.event.domain.model;

import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an envelope that has exhausted all delivery attempts and been
 * moved to the Dead-Letter Queue (DLQ).
 *
 * <p>Dead-letter entries can be:
 * <ul>
 *   <li><strong>Reprocessed</strong> — after the root cause has been fixed,
 *       an operator triggers reprocessing via the management API.</li>
 *   <li><strong>Discarded</strong> — intentionally dropped when the event is
 *       no longer relevant (e.g. the aggregate no longer exists).</li>
 * </ul>
 *
 * <p>All state transitions are recorded with a timestamp for full auditability.
 */
public class DeadLetterEntry {

    private final DeadLetterEntryId id;
    private final EnvelopeId        originalEnvelopeId;
    private final String            kafkaTopic;
    private final String            originalPayload;
    private final String            failureReason;
    private final LocalDateTime     failedAt;
    private final RetryPolicy       retryPolicy;
    private DLQStatus               status;
    private LocalDateTime           reprocessedAt;
    private String                  discardReason;

    // ── Constructor ──────────────────────────────────────────────────────────

    private DeadLetterEntry(
            final DeadLetterEntryId id,
            final EnvelopeId originalEnvelopeId,
            final String kafkaTopic,
            final String originalPayload,
            final String failureReason,
            final RetryPolicy retryPolicy) {
        this.id                 = Objects.requireNonNull(id);
        this.originalEnvelopeId = Objects.requireNonNull(originalEnvelopeId);
        this.kafkaTopic         = Objects.requireNonNull(kafkaTopic);
        this.originalPayload    = Objects.requireNonNull(originalPayload);
        this.failureReason      = failureReason;
        this.failedAt           = LocalDateTime.now();
        this.retryPolicy        = retryPolicy != null ? retryPolicy : RetryPolicy.defaultDlqPolicy();
        this.status             = DLQStatus.WAITING;
    }

    /**
     * Private constructor for restore() — allows setting failedAt explicitly.
     */
    private DeadLetterEntry(
            final DeadLetterEntryId id,
            final EnvelopeId originalEnvelopeId,
            final String kafkaTopic,
            final String originalPayload,
            final String failureReason,
            final LocalDateTime failedAt,
            final RetryPolicy retryPolicy) {
        this.id                 = Objects.requireNonNull(id);
        this.originalEnvelopeId = Objects.requireNonNull(originalEnvelopeId);
        this.kafkaTopic         = Objects.requireNonNull(kafkaTopic);
        this.originalPayload    = Objects.requireNonNull(originalPayload);
        this.failureReason      = failureReason;
        this.failedAt           = Objects.requireNonNull(failedAt);
        this.retryPolicy        = retryPolicy != null ? retryPolicy : RetryPolicy.defaultDlqPolicy();
        this.status             = DLQStatus.WAITING;
    }

    // ── Static factory ───────────────────────────────────────────────────────

    /**
     * Creates a dead-letter entry from a failed envelope.
     *
     * @param envelope      the envelope that exhausted all retry attempts
     * @param failureReason the last recorded error message
     * @return a new DLQ entry in {@link DLQStatus#WAITING} state
     */
    public static DeadLetterEntry from(
            final DomainEventEnvelope envelope,
            final String failureReason) {
        return new DeadLetterEntry(
            DeadLetterEntryId.generate(),
            envelope.getId(),
            envelope.getKafkaTopic(),
            envelope.getPayload(),
            failureReason,
            envelope.getRetryPolicy()
        );
    }

    /**
     * Reconstructs a {@code DeadLetterEntry} from persisted data without
     * going through state-machine transitions.
     *
     * <p>Intended exclusively for the persistence adapter layer.
     *
     * @param id              persisted DLQ entry identifier
     * @param originalEnvelopeId persisted original envelope reference
     * @param kafkaTopic      persisted target topic
     * @param originalPayload persisted event payload
     * @param failureReason   persisted failure description
     * @param status          persisted DLQ lifecycle status
     * @param failedAt        persisted failure timestamp
     * @param reprocessedAt   persisted reprocessing timestamp (may be {@code null})
     * @param discardReason   persisted discard justification (may be {@code null})
     * @param retryPolicy     retry configuration to apply if reprocessed
     * @return fully-restored DLQ entry
     */
    public static DeadLetterEntry restore(
            final DeadLetterEntryId id,
            final EnvelopeId originalEnvelopeId,
            final String kafkaTopic,
            final String originalPayload,
            final String failureReason,
            final DLQStatus status,
            final java.time.LocalDateTime failedAt,
            final java.time.LocalDateTime reprocessedAt,
            final String discardReason,
            final RetryPolicy retryPolicy) {

        DeadLetterEntry entry = new DeadLetterEntry(
            id, originalEnvelopeId, kafkaTopic, originalPayload,
            failureReason, failedAt, retryPolicy
        );
        entry.status          = status != null ? status : DLQStatus.WAITING;
        entry.reprocessedAt   = reprocessedAt;
        entry.discardReason   = discardReason;
        return entry;
    }

    // ── State transitions ────────────────────────────────────────────────────

    /**
     * Initiates reprocessing of this dead-letter entry.
     *
     * @throws IllegalStateException if the entry is not in WAITING state
     */
    public void reprocess() {
        if (status != DLQStatus.WAITING) {
            throw new IllegalStateException(
                "Can only reprocess entries in WAITING state, current: " + status);
        }
        this.status = DLQStatus.REPROCESSING;
    }

    /**
     * Marks the reprocessing as successful.
     *
     * @throws IllegalStateException if the entry is not in REPROCESSING state
     */
    public void markReprocessed() {
        if (status != DLQStatus.REPROCESSING) {
            throw new IllegalStateException("Entry is not in REPROCESSING state");
        }
        this.status          = DLQStatus.REPROCESSED;
        this.reprocessedAt   = LocalDateTime.now();
    }

    /**
     * Explicitly discards this entry; it will not be retried again.
     *
     * @param reason the administrative justification for discarding
     */
    public void discard(final String reason) {
        if (status == DLQStatus.REPROCESSED) {
            throw new IllegalStateException("Cannot discard an already reprocessed entry");
        }
        this.status        = DLQStatus.DISCARDED;
        this.discardReason = reason;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public DeadLetterEntryId getId()                { return id; }
    public EnvelopeId        getOriginalEnvelopeId(){ return originalEnvelopeId; }
    public String            getKafkaTopic()        { return kafkaTopic; }
    public String            getOriginalPayload()   { return originalPayload; }
    public String            getFailureReason()     { return failureReason; }
    public LocalDateTime     getFailedAt()          { return failedAt; }
    public RetryPolicy       getRetryPolicy()       { return retryPolicy; }
    public DLQStatus         getStatus()            { return status; }
    public LocalDateTime     getReprocessedAt()     { return reprocessedAt; }
    public String            getDiscardReason()     { return discardReason; }
}
