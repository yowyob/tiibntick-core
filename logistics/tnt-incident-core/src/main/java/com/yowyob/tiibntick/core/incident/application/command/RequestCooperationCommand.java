package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.CooperationType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to initiate an inter-agency cooperation request.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class RequestCooperationCommand {
    @NotNull UUID incidentId;
    @NotNull UUID requestingAgencyId;
    @NotNull UUID respondingAgencyId;
    @NotNull CooperationType cooperationType;
    @NotNull String details;
    @NotNull UUID requestedByActorId;
}
