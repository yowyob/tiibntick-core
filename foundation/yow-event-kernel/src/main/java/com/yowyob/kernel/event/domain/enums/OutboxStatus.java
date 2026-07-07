package com.yowyob.kernel.event.domain.enums;

/**
 * Processing status of an {@link yowyob.kernel.event.domain.model.OutboxEntry}.
 *
 * <p>State machine:
 * <pre>
 *   PENDING → PROCESSING → PROCESSED
 *                        → FAILED → RETRYING → PROCESSED
 *                                            → DEAD
 * </pre>
 */
public enum OutboxStatus {

    /** Waiting to be picked up by the outbox poller. */
    PENDING,

    /** Currently being processed by the poller (lock acquired). */
    PROCESSING,

    /** Successfully published to Kafka and committed to the event store. */
    PROCESSED,

    /** Processing failed; will be retried. */
    FAILED,

    /** Scheduled for retry with exponential back-off delay. */
    RETRYING
}
