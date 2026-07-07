package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.*;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentInterAgencyCooperation;
import reactor.core.publisher.Mono;
/**
 * Inbound port: manage the full inter-agency cooperation lifecycle.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IInterAgencyCooperationUseCase {
    Mono<IncidentInterAgencyCooperation> request(RequestCooperationCommand command);
    Mono<IncidentInterAgencyCooperation> accept(RespondToCooperationCommand command);
    Mono<IncidentInterAgencyCooperation> reject(RespondToCooperationCommand command);
    Mono<IncidentInterAgencyCooperation> complete(RecordCooperationCompletionCommand command);
}
