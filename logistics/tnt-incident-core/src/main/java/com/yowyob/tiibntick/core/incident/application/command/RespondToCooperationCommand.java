package com.yowyob.tiibntick.core.incident.application.command;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to accept or reject a pending inter-agency cooperation request.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class RespondToCooperationCommand {
    @NotNull UUID cooperationId;
    @NotNull UUID respondingAgencyId;
    @NotNull UUID respondedByActorId;
    String responseDetails;
    String rejectionReason;
}
