package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO carrying triage context parameters.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class TriageRequest {
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
