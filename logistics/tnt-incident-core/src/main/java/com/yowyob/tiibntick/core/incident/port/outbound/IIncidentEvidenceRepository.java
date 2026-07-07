package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEvidence;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: persistence of digital evidence attached to incidents.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentEvidenceRepository {
    Mono<IncidentEvidence> save(IncidentEvidence evidence);
    Flux<IncidentEvidence> findEvidenceByIncidentId(UUID incidentId);
    Mono<IncidentEvidence> findEvidenceById(UUID id);
}
