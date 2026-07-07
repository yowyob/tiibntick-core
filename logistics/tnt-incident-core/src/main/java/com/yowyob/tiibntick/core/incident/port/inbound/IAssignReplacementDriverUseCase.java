package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.AssignReplacementDriverCommand;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentDriverReplacement;
import reactor.core.publisher.Mono;
/**
 * Inbound port: assign a replacement driver to an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IAssignReplacementDriverUseCase {
    Mono<IncidentDriverReplacement> execute(AssignReplacementDriverCommand command);
}
