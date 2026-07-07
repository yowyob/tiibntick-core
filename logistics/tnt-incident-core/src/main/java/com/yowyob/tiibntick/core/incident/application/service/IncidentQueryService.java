package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.query.AgencyIncidentKpi;
import com.yowyob.tiibntick.core.incident.application.query.ListIncidentsQuery;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentBlockchainRecord;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IQueryIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentBlockchainRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventLogRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service exposing read-only query operations over incidents and their blockchain chains.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentQueryService implements IQueryIncidentUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentBlockchainRepository blockchainRepository;

    @Override
    public Mono<Incident> getById(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)));
    }

    @Override
    public Mono<Incident> getByReferenceCode(String referenceCode) {
        return incidentRepository.findByReferenceCode(referenceCode)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + referenceCode)));
    }

    @Override
    public Flux<Incident> listByAgency(ListIncidentsQuery query) {
        if (query.getStatus() != null) {
            return incidentRepository.findByAgencyIdAndStatus(query.getAgencyId(), query.getStatus());
        }
        return incidentRepository.findByAgencyIdAndCreatedBetween(
                query.getAgencyId(),
                query.getFrom() != null ? query.getFrom() : Instant.now().minusSeconds(86400 * 30),
                query.getTo() != null ? query.getTo() : Instant.now()
        );
    }

    @Override
    public Flux<IncidentEventLog> getTimeline(UUID incidentId) {
        return eventLogRepository.findByIncidentIdOrderByOccurredAt(incidentId);
    }

    @Override
    public Flux<IncidentBlockchainRecord> getBlockchainChain(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .filter(inc -> inc.getOwnBlockchainChainId() != null)
                .flatMapMany(inc -> blockchainRepository.findByChainIdOrderByBlockIndex(inc.getOwnBlockchainChainId()));
    }

    @Override
    public Mono<AgencyIncidentKpi> getAgencyKpi(UUID agencyId, UUID tenantId) {
        return Mono.zip(
                incidentRepository.countActiveByAgency(agencyId),
                incidentRepository.findByAgencyIdAndStatus(agencyId, IncidentStatus.RESOLVED).count(),
                incidentRepository.findByAgencyIdAndStatus(agencyId, IncidentStatus.ESCALATED).count(),
                incidentRepository.findEscalatedIncidents(tenantId).count()
        ).map(tuple -> AgencyIncidentKpi.builder()
                .agencyId(agencyId)
                .totalActive(tuple.getT1())
                .totalResolved(tuple.getT2())
                .totalEscalated(tuple.getT3())
                .totalInterAgency(0L)
                .slaBreaches(0L)
                .avgResolutionMinutes(0.0)
                .last24hIncidents(0L)
                .build());
    }
}
