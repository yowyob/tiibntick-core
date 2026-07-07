package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: persistence of ordered incident event log entries.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentEventLogRepository {
    Mono<IncidentEventLog> save(IncidentEventLog log);
    Flux<IncidentEventLog> findByIncidentIdOrderByOccurredAt(UUID incidentId);
}
