package com.yowyob.kernel.event.adapter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer-based implementation of {@link EventMetricsPort}.
 *
 * <p>Metrics are registered lazily on first use (keyed by tag values) so that
 * a potentially unbounded number of {@code (eventType, solutionCode, tenantId)}
 * tag combinations does not pre-allocate memory at startup.
 *
 * <p>All recording methods are synchronous and guaranteed not to throw, as
 * required by the port contract.
 */
@Component
public class MicrometerEventMetrics implements EventMetricsPort {

    private static final String METRIC_PUBLISHED   = "yow.event.published.total";
    private static final String METRIC_FAILED      = "yow.event.failed.total";
    private static final String METRIC_DLQ_MOVED   = "yow.event.dlq.total";
    private static final String METRIC_DURATION    = "yow.event.publish.duration";
    private static final String METRIC_OUTBOX_PEND = "yow.event.outbox.pending";
    private static final String METRIC_DLQ_PEND    = "yow.event.dlq.pending";

    private final MeterRegistry registry;

    // Gauge backing values
    private final AtomicLong outboxBacklog = new AtomicLong(0);
    private final AtomicLong dlqSize       = new AtomicLong(0);

    public MicrometerEventMetrics(final MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        // Register gauges once — they read from the AtomicLong references
        registry.gauge(METRIC_OUTBOX_PEND, outboxBacklog, AtomicLong::get);
        registry.gauge(METRIC_DLQ_PEND,    dlqSize,       AtomicLong::get);
    }

    @Override
    public void recordPublished(final DomainEventEnvelope envelope, final long durationMs) {
        try {
            counter(METRIC_PUBLISHED, envelope, null).increment();
            timer(envelope).record(Duration.ofMillis(durationMs));
        } catch (Exception ignored) {
            // Metric failures must never disrupt the event pipeline
        }
    }

    @Override
    public void recordFailed(final DomainEventEnvelope envelope, final String reason) {
        try {
            Counter.builder(METRIC_FAILED)
                .tag("eventType", envelope.getEventType())
                .tag("solutionCode", envelope.getSolutionCode())
                .tag("tenantId", envelope.getTenantId())
                .tag("reason", reason != null ? reason : "unknown")
                .register(registry)
                .increment();
        } catch (Exception ignored) {}
    }

    @Override
    public void recordMovedToDlq(final DomainEventEnvelope envelope) {
        try {
            counter(METRIC_DLQ_MOVED, envelope, null).increment();
        } catch (Exception ignored) {}
    }

    @Override
    public void updateOutboxBacklog(final long pendingCount) {
        outboxBacklog.set(pendingCount);
    }

    @Override
    public void updateDlqSize(final long size) {
        dlqSize.set(size);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Counter counter(
            final String name,
            final DomainEventEnvelope envelope,
            final String extraTagValue) {
        return Counter.builder(name)
            .tag("eventType",    envelope.getEventType())
            .tag("solutionCode", envelope.getSolutionCode())
            .tag("tenantId",     envelope.getTenantId())
            .register(registry);
    }

    private Timer timer(final DomainEventEnvelope envelope) {
        return Timer.builder(METRIC_DURATION)
            .tag("eventType",    envelope.getEventType())
            .tag("solutionCode", envelope.getSolutionCode())
            .register(registry);
    }
}
