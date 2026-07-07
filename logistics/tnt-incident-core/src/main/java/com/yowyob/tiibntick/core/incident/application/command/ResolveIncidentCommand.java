package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.ResolutionMode;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to mark an incident as resolved with a chosen resolution mode.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class ResolveIncidentCommand {
    @NotNull UUID incidentId;
    @NotNull UUID resolvedByActorId;
    @NotNull ResolutionMode resolutionMode;
    String resolutionNotes;
}
