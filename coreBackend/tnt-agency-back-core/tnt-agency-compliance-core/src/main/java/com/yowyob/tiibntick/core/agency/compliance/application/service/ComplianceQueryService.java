package com.yowyob.tiibntick.core.agency.compliance.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.DisputeCorePort;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.IncidentCorePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Read side for compliance: lists and details of disputes and incidents.
 * Ported from the BFF {@code ListComplianceService}.
 */
@Service
public class ComplianceQueryService {

    private final ComplianceOrchestrator orchestrator;
    private final DisputeCorePort disputeCore;
    private final IncidentCorePort incidentCore;

    public ComplianceQueryService(ComplianceOrchestrator orchestrator,
                                  DisputeCorePort disputeCore,
                                  IncidentCorePort incidentCore) {
        this.orchestrator = orchestrator;
        this.disputeCore = disputeCore;
        this.incidentCore = incidentCore;
    }

    public Mono<DisputeCorePort.DisputePage> listDisputes(
            UUID agencyId, UUID tenantId, String status, int page, int size) {
        if (!orchestrator.isEnabled()) {
            return Mono.just(new DisputeCorePort.DisputePage(List.of(), page, size, 0));
        }
        return disputeCore.listDisputes(new DisputeCorePort.ListDisputesRequest(
                tenantId, agencyId, status, page, size));
    }

    public Flux<IncidentCorePort.IncidentSummary> listIncidents(
            UUID agencyId, UUID tenantId, String status, int page, int size) {
        if (!orchestrator.isEnabled()) {
            return Flux.empty();
        }
        return incidentCore.listIncidents(new IncidentCorePort.ListIncidentsRequest(
                tenantId, agencyId, status, page, size));
    }

    public Mono<DisputeCorePort.DisputeDetail> getDispute(UUID tenantId, String disputeId) {
        if (!orchestrator.isEnabled()) {
            return Mono.error(new TntNotFoundException(
                    "DISPUTE_NOT_FOUND", "Litige introuvable (compliance désactivé)"));
        }
        return disputeCore.getDispute(tenantId, disputeId);
    }

    public Mono<IncidentCorePort.IncidentDetail> getIncident(UUID incidentId) {
        if (!orchestrator.isEnabled()) {
            return Mono.error(new TntNotFoundException(
                    "INCIDENT_NOT_FOUND", "Incident introuvable (compliance désactivé)"));
        }
        return incidentCore.getIncident(incidentId);
    }
}
