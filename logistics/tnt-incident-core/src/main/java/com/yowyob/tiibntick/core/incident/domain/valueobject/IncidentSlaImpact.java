package com.yowyob.tiibntick.core.incident.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * SLA impact assessment: delay estimation, revised deadline and breach indicators.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value
@Builder
public class IncidentSlaImpact {
    UUID incidentId;
    Instant originalSlaDeadline;
    long estimatedDelayMinutes;
    Instant revisedDeadline;
    boolean slaBreached;
    long breachMinutes;
    boolean penaltyApplicable;

    public static IncidentSlaImpact compute(UUID incidentId, Instant originalDeadline, long delayMinutes) {
        Instant revised = originalDeadline.plusSeconds(delayMinutes * 60);
        boolean breached = Instant.now().isAfter(originalDeadline);
        long breach = breached ? java.time.Duration.between(originalDeadline, Instant.now()).toMinutes() : 0L;
        return IncidentSlaImpact.builder()
                .incidentId(incidentId)
                .originalSlaDeadline(originalDeadline)
                .estimatedDelayMinutes(delayMinutes)
                .revisedDeadline(revised)
                .slaBreached(breached)
                .breachMinutes(breach)
                .penaltyApplicable(breach > 30)
                .build();
    }
}
