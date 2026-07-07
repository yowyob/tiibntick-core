package com.yowyob.tiibntick.core.incident.application.command;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to assign a replacement driver and vehicle to an ongoing incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class AssignReplacementDriverCommand {
    @NotNull UUID incidentId;
    @NotNull UUID replacementDriverId;
    @NotNull UUID replacementVehicleId;
    @NotNull UUID replacementAgencyId;
    @NotNull UUID assignedByActorId;
    boolean manualAssignment;
}
