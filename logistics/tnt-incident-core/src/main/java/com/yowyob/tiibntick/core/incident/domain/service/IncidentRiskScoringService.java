package com.yowyob.tiibntick.core.incident.domain.service;

import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentRiskScore;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pure domain service computing a weighted risk score across eight factors to drive auto-resolution decisions.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public class IncidentRiskScoringService {

    /**
     * Computes the global risk score for an incident based on eight weighted factors.
     *
     * <p>A score above 0.7 indicates that human intervention is required.
     * A score below 0.6 qualifies for fully automatic resolution.
     *
     * @param incident              the incident being scored
     * @param driverReputationScore driver reputation (0 = worst, 1 = best)
     * @param parcelValueNormalized normalized parcel value (0 to 1)
     * @param zoneDangerIndex       geographical danger index (0 to 1)
     * @param cargoSensitivity      cargo sensitivity level (0 to 1)
     * @param weatherIndex          adverse weather index (0 to 1)
     * @param driverIncidentHistory number of past incidents for this driver
     * @param missionComplexity     mission complexity factor (0 to 1)
     * @return a fully computed {@link com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentRiskScore}
     */
    public IncidentRiskScore compute(Incident incident, double driverReputationScore,
                                     double parcelValueNormalized, double zoneDangerIndex,
                                     double cargoSensitivity, double weatherIndex,
                                     int driverIncidentHistory, double missionComplexity) {

        Map<RiskFactor, Double> factors = new EnumMap<>(RiskFactor.class);
        factors.put(RiskFactor.DRIVER_REPUTATION_SCORE, 1.0 - Math.min(1.0, driverReputationScore));
        factors.put(RiskFactor.PARCEL_VALUE, Math.min(1.0, parcelValueNormalized));
        factors.put(RiskFactor.ZONE_DANGER_INDEX, Math.min(1.0, zoneDangerIndex));
        factors.put(RiskFactor.DELAY_SEVERITY, computeDelaySeverity(incident));
        factors.put(RiskFactor.CARGO_SENSITIVITY, Math.min(1.0, cargoSensitivity));
        factors.put(RiskFactor.WEATHER_CONDITIONS, Math.min(1.0, weatherIndex));
        factors.put(RiskFactor.INCIDENT_HISTORY_DRIVER, Math.min(1.0, driverIncidentHistory / 10.0));
        factors.put(RiskFactor.MISSION_COMPLEXITY, Math.min(1.0, missionComplexity));

        double globalScore = (
                factors.get(RiskFactor.DRIVER_REPUTATION_SCORE) * 0.20 +
                factors.get(RiskFactor.PARCEL_VALUE) * 0.15 +
                factors.get(RiskFactor.ZONE_DANGER_INDEX) * 0.20 +
                factors.get(RiskFactor.DELAY_SEVERITY) * 0.15 +
                factors.get(RiskFactor.CARGO_SENSITIVITY) * 0.10 +
                factors.get(RiskFactor.WEATHER_CONDITIONS) * 0.05 +
                factors.get(RiskFactor.INCIDENT_HISTORY_DRIVER) * 0.10 +
                factors.get(RiskFactor.MISSION_COMPLEXITY) * 0.05
        );

        boolean autoResolutionRecommended = globalScore < 0.6
                && incident.getSeverity().getLevel() <= IncidentSeverity.HIGH.getLevel()
                && !incident.getSeverity().requiresImmediateEscalation();

        String recommendedAction = determineAction(incident, globalScore);

        return IncidentRiskScore.builder()
                .incidentId(incident.getId())
                .globalScore(Math.min(1.0, globalScore))
                .factorScores(factors)
                .computedAt(Instant.now())
                .autoResolutionRecommended(autoResolutionRecommended)
                .recommendedAction(recommendedAction)
                .confidenceLevel(0.85)
                .build();
    }

    private double computeDelaySeverity(Incident incident) {
        if (incident.getSlaImpact() == null) return 0.3;
        long breach = incident.getSlaImpact().getBreachMinutes();
        if (breach <= 0) return 0.1;
        if (breach <= 15) return 0.3;
        if (breach <= 30) return 0.5;
        if (breach <= 60) return 0.7;
        return 1.0;
    }

    private String determineAction(Incident incident, double score) {
        if (incident.getSeverity().requiresImmediateEscalation()) {
            return "IMMEDIATE_ESCALATION_REQUIRED";
        }
        if (incident.requiresDriverReplacement()) {
            return "AUTO_REASSIGN_DRIVER";
        }
        if (score >= 0.8) {
            return "ESCALATE_TO_AGENCY";
        }
        if (score >= 0.6) {
            return "HYBRID_AUTO_THEN_AGENCY";
        }
        return "FULL_AUTO_RESOLUTION";
    }
}
