package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentDriverReplacement;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: persistence of driver replacement process records.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentDriverReplacementRepository {
    Mono<IncidentDriverReplacement> save(IncidentDriverReplacement replacement);
    Mono<IncidentDriverReplacement> findReplacementByIncidentId(UUID incidentId);
}
