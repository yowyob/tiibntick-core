package com.yowyob.kernel.event.application.port.out;

import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

/**
 * <b>Outbound port</b> — Records observability metrics for the event bus.
 *
 * <p>Implementations delegate to Micrometer, which in turn exports metrics to
 * Prometheus via the {@code /actuator/prometheus} endpoint and to any other
 * configured registries (Grafana, Datadog, etc.).
 *
 * <p>All methods are synchronous and must not throw; metric recording failures
 * must be silently swallowed to avoid interfering with the publishing pipeline.
 *
 * <h2>Metric names</h2>
 * <ul>
 *   <li>{@code yow.event.published.total} — counter, tagged with {@code eventType},
 *       {@code solutionCode}, {@code tenantId}</li>
 *   <li>{@code yow.event.failed.total} — counter, same tags</li>
 *   <li>{@code yow.event.dlq.total} — counter, same tags</li>
 *   <li>{@code yow.event.publish.duration} — timer (milliseconds)</li>
 *   <li>{@code yow.event.outbox.pending} — gauge</li>
 *   <li>{@code yow.event.dlq.pending} — gauge</li>
 * </ul>
 */
public interface EventMetricsPort {

    /**
     * Increments the published-events counter for the given envelope.
     *
     * @param envelope     the successfully published envelope
     * @param durationMs   end-to-end publish latency in milliseconds
     */
    void recordPublished(DomainEventEnvelope envelope, long durationMs);

    /**
     * Increments the failed-events counter for the given envelope.
     *
     * @param envelope  the envelope that failed to publish
     * @param reason    a short label describing the failure cause (used as a tag)
     */
    void recordFailed(DomainEventEnvelope envelope, String reason);

    /**
     * Increments the DLQ counter when an envelope is moved to the dead-letter queue.
     *
     * @param envelope the envelope that was moved to DLQ
     */
    void recordMovedToDlq(DomainEventEnvelope envelope);

    /**
     * Updates the outbox backlog gauge with the current pending count.
     *
     * @param pendingCount the number of PENDING outbox entries
     */
    void updateOutboxBacklog(long pendingCount);

    /**
     * Updates the DLQ size gauge.
     *
     * @param dlqSize the current number of WAITING DLQ entries
     */
    void updateDlqSize(long dlqSize);
}
