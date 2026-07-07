package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.StartAgencyHandlingCommand;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;
/**
 * Inbound port: transfer an incident to agency manual handling.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IStartAgencyHandlingUseCase {
    Mono<Incident> execute(StartAgencyHandlingCommand command);
}
