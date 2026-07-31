package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.query.AgencyIncidentKpi;
import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.incident.application.query.ListIncidentsQuery;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentBlockchainRecord;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IQueryIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentBlockchainRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventLogRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentRepository;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
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

    /**
     * Enforces tenant isolation (unconditional) and, for non-privileged callers,
     * actor-level ownership (must be the incident's own reporter) — 404 either way,
     * so a caller can't distinguish "doesn't exist" from "not yours to see."
     */
    private Mono<Incident> assertAccessible(Incident incident, IncidentRequesterContext requester) {
        boolean tenantMatches = requester.tenantId() != null && requester.tenantId().equals(incident.getTenantId());
        boolean owns = incident.getReportedByActorId() != null
                && incident.getReportedByActorId().equals(requester.actorId());
        if (!tenantMatches || (!requester.privileged() && !owns)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Incident not found: " + incident.getId()));
        }
        return Mono.just(incident);
    }

    @Override
    @RequirePermission(resource = "incident", action = "read")
    public Mono<Incident> getById(UUID incidentId, IncidentRequesterContext requester) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)))
                .flatMap(incident -> assertAccessible(incident, requester));
    }

    @Override
    @RequirePermission(resource = "incident", action = "read")
    public Mono<Incident> getByReferenceCode(String referenceCode, IncidentRequesterContext requester) {
        return incidentRepository.findByReferenceCode(referenceCode)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + referenceCode)))
                .flatMap(incident -> assertAccessible(incident, requester));
    }

    @Override
    @RequirePermission(resource = "incident", action = "read")
    public Flux<Incident> listByAgency(ListIncidentsQuery query) {
        if (query.getTenantId() == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "tenantId is required"));
        }
        Flux<Incident> results;
        if (query.getAgencyId() != null) {
            results = query.getStatus() != null
                    ? incidentRepository.findByAgencyIdAndStatus(query.getAgencyId(), query.getStatus())
                    : incidentRepository.findByAgencyIdAndCreatedBetween(
                            query.getAgencyId(),
                            query.getFrom() != null ? query.getFrom() : Instant.now().minusSeconds(86400 * 30),
                            query.getTo() != null ? query.getTo() : Instant.now()
                    );
        } else {
            // No agency to filter by — GO/FREELANCER-platform caller listing their own incidents.
            if (query.getRequesterActorId() == null) {
                return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "agencyId or an authenticated actor is required"));
            }
            results = incidentRepository.findByReportedByActorIdAndCreatedBetween(
                    query.getRequesterActorId(), query.getTenantId(),
                    query.getFrom() != null ? query.getFrom() : Instant.now().minusSeconds(86400 * 30),
                    query.getTo() != null ? query.getTo() : Instant.now()
            );
            if (query.getStatus() != null) {
                results = results.filter(inc -> query.getStatus().equals(inc.getStatus()));
            }
        }
        return results
                .filter(inc -> query.getTenantId().equals(inc.getTenantId()))
                .filter(inc -> query.isPrivileged()
                        || (inc.getReportedByActorId() != null && inc.getReportedByActorId().equals(query.getRequesterActorId())));
    }

    @Override
    @RequirePermission(resource = "incident", action = "read")
    public Flux<IncidentEventLog> getTimeline(UUID incidentId, IncidentRequesterContext requester) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)))
                .flatMap(incident -> assertAccessible(incident, requester))
                .flatMapMany(incident -> eventLogRepository.findByIncidentIdOrderByOccurredAt(incidentId));
    }

    @Override
    @RequirePermission(resource = "incident", action = "read")
    public Flux<IncidentBlockchainRecord> getBlockchainChain(UUID incidentId, IncidentRequesterContext requester) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)))
                .flatMap(incident -> assertAccessible(incident, requester))
                .filter(inc -> inc.getOwnBlockchainChainId() != null)
                .flatMapMany(inc -> blockchainRepository.findByChainIdOrderByBlockIndex(inc.getOwnBlockchainChainId()));
    }

    @Override
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<AgencyIncidentKpi> getAgencyKpi(UUID agencyId, UUID tenantId, IncidentRequesterContext requester) {
        if (requester.tenantId() == null || !requester.tenantId().equals(tenantId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch"));
        }
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
