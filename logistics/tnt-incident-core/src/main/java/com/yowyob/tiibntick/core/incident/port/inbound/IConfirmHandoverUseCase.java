package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.ConfirmHandoverCommand;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentDriverReplacement;
import reactor.core.publisher.Mono;
/**
 * Inbound port: confirm the physical parcel handover between drivers.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IConfirmHandoverUseCase {
    Mono<IncidentDriverReplacement> execute(ConfirmHandoverCommand command);
}
