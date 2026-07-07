package com.yowyob.tiibntick.core.incident.domain.valueobject;

import com.yowyob.tiibntick.core.incident.domain.enums.RiskFactor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Computed risk score (0 to 1) broken down across eight weighted factors.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value
@Builder
public class IncidentRiskScore {
    UUID incidentId;
    double globalScore;
    Map<RiskFactor, Double> factorScores;
    Instant computedAt;
    boolean autoResolutionRecommended;
    String recommendedAction;
    double confidenceLevel;

    public boolean requiresHumanIntervention() {
        return globalScore >= 0.7 || !autoResolutionRecommended;
    }

    public boolean isHighRisk() {
        return globalScore >= 0.8;
    }
}
