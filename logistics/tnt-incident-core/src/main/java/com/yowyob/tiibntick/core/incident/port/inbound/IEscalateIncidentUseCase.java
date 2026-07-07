package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.EscalateIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;
/**
 * Inbound port: escalate an incident to a higher authority or dispute.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IEscalateIncidentUseCase {
    Mono<Incident> execute(EscalateIncidentCommand command);
}
