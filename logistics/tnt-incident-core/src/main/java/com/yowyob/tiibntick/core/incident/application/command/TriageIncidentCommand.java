package com.yowyob.tiibntick.core.incident.application.command;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to trigger the triage phase: severity assessment, geo-snapshot and risk scoring.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class TriageIncidentCommand {
    @NotNull UUID incidentId;
    @NotNull UUID triggeredByActorId;
    double driverReputationScore;
    double parcelValueNormalized;
    double zoneDangerIndex;
    double cargoSensitivity;
    double weatherIndex;
    int driverIncidentHistory;
    double missionComplexity;
    Long slaDeadlineEpochSeconds;
}
