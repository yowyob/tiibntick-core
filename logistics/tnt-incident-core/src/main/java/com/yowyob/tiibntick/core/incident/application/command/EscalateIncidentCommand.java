package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to escalate an incident to a higher authority, optionally triggering a dispute.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class EscalateIncidentCommand {
    @NotNull UUID incidentId;
    @NotNull UUID escalatedByActorId;
    @NotNull ActorRole escalatedByRole;
    UUID targetActorId;
    @NotNull ActorRole targetRole;
    @NotNull String reason;
    boolean triggerDispute;
    String fraudEvidence;
}
