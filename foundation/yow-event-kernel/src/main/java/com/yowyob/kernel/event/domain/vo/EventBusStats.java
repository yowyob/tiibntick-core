package com.yowyob.kernel.event.domain.vo;

import java.time.LocalDateTime;

/**
 * Snapshot of event bus health and throughput statistics.
 *
 * <p>Returned by the {@link yowyob.kernel.event.application.port.in.QueryEventStatsUseCase}
 * and exposed via the {@code /actuator/yow-event} endpoint.
 *
 * @param totalPublished          total envelopes successfully published since start
 * @param totalFailed             total envelopes that entered the FAILED state
 * @param totalDead               total envelopes permanently moved to the DLQ
 * @param pendingInOutbox         current number of PENDING outbox entries
 * @param avgPublishLatencyMs     rolling average publish latency in milliseconds
 * @param dlqSize                 current number of entries waiting in the DLQ
 * @param schemaCount             number of registered Avro schemas
 * @param generatedAt             the instant this snapshot was generated
 */
public record EventBusStats(
        long totalPublished,
        long totalFailed,
        long totalDead,
        long pendingInOutbox,
        double avgPublishLatencyMs,
        long dlqSize,
        int schemaCount,
        LocalDateTime generatedAt
) {

    /**
     * Returns {@code true} when the DLQ contains entries that require attention.
     * The threshold is deliberately low (any entry) to encourage prompt resolution.
     *
     * @return {@code true} if {@code dlqSize > 0}
     */
    public boolean hasDeadLetterEntries() {
        return dlqSize > 0;
    }

    /**
     * Returns {@code true} if the outbox backlog is large enough to indicate
     * a potential publishing bottleneck (threshold: 500 pending entries).
     *
     * @return {@code true} if {@code pendingInOutbox >= 500}
     */
    public boolean hasOutboxBacklog() {
        return pendingInOutbox >= 500;
    }

    /**
     * Computes the overall failure rate as a value between 0.0 and 1.0.
     *
     * @return failure rate, or 0.0 when no envelopes have been processed
     */
    public double failureRate() {
        long processed = totalPublished + totalFailed + totalDead;
        if (processed == 0) {
            return 0.0;
        }
        return (double) (totalFailed + totalDead) / processed;
    }

    /**
     * Factory for an empty statistics snapshot (useful for initial state or tests).
     *
     * @return a zero-value statistics snapshot
     */
    public static EventBusStats empty() {
        return new EventBusStats(0L, 0L, 0L, 0L, 0.0, 0L, 0, LocalDateTime.now());
    }
}
