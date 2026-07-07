package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.AttachEvidenceCommand;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEvidence;
import reactor.core.publisher.Mono;
/**
 * Inbound port: attach a digital evidence file to an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IAttachEvidenceUseCase {
    Mono<IncidentEvidence> execute(AttachEvidenceCommand command);
}
