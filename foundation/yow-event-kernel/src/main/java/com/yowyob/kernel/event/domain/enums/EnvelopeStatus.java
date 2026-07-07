package com.yowyob.kernel.event.domain.enums;

/**
 * Lifecycle status of a {@link yowyob.kernel.event.domain.model.DomainEventEnvelope}.
 *
 * <p>State machine:
 * <pre>
 *   PENDING → PUBLISHED
 *           → FAILED → DEAD
 *           → RETRYING → PUBLISHED
 *                     → DEAD
 * </pre>
 */
public enum EnvelopeStatus {

    /**
     * The envelope has been persisted in the outbox but has not yet been
     * picked up by the {@link yowyob.kernel.event.application.port.out.OutboxPollerPort}.
     */
    PENDING,

    /**
     * The envelope has been successfully published to the Kafka topic and
     * the consumer has acknowledged reception (or the fire-and-forget policy
     * is satisfied).
     */
    PUBLISHED,

    /**
     * Publishing failed at least once. The envelope will be retried according
     * to the attached {@link yowyob.kernel.event.domain.vo.RetryPolicy}.
     */
    FAILED,

    /**
     * All retry attempts have been exhausted. The envelope has been moved to
     * the dead-letter queue (DLQ) for manual inspection.
     */
    DEAD,

    /**
     * The envelope is currently being retried by the poller.
     */
    RETRYING
}
