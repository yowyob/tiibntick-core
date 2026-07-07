package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.query.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Inbound port: read-only query operations over incidents.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IQueryIncidentUseCase {
    Mono<Incident> getById(UUID incidentId);
    Mono<Incident> getByReferenceCode(String referenceCode);
    Flux<Incident> listByAgency(ListIncidentsQuery query);
    Flux<IncidentEventLog> getTimeline(UUID incidentId);
    Flux<IncidentBlockchainRecord> getBlockchainChain(UUID incidentId);
    Mono<AgencyIncidentKpi> getAgencyKpi(UUID agencyId, UUID tenantId);
}
