package com.yowyob.tiibntick.core.incident.application.command;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to record successful completion of an inter-agency cooperation.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class RecordCooperationCompletionCommand {
    @NotNull UUID cooperationId;
    @NotNull UUID completedByActorId;
}
