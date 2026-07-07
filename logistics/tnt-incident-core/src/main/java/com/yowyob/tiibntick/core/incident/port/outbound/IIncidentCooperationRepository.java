package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentInterAgencyCooperation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: persistence of inter-agency cooperation records.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentCooperationRepository {
    Mono<IncidentInterAgencyCooperation> save(IncidentInterAgencyCooperation coop);
    Flux<IncidentInterAgencyCooperation> findCooperationByIncidentId(UUID incidentId);
    Mono<IncidentInterAgencyCooperation> findCooperationById(UUID id);
    Flux<IncidentInterAgencyCooperation> findActiveByRespondingAgency(UUID agencyId);
}
