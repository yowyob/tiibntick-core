package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.vo.EventBusStats;

/**
 * <b>Inbound port</b> — Retrieve operational statistics for the event bus.
 *
 * <p>Metrics are computed in real time from the outbox and DLQ tables and
 * exposed via the {@code /actuator/yow-event/stats} endpoint.
 */
public interface QueryEventStatsUseCase {

    /**
     * Returns a current snapshot of event bus health metrics.
     *
     * @param tenantId the tenant for which to compute statistics,
     *                 or {@code null} to aggregate across all tenants
     *                 (admin role required)
     * @return a {@link Mono} emitting the statistics snapshot
     */
    Mono<EventBusStats> getStats(String tenantId);
}
