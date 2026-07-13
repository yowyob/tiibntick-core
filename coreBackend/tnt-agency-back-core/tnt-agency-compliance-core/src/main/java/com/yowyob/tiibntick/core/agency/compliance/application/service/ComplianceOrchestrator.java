package com.yowyob.tiibntick.core.agency.compliance.application.service;

import com.yowyob.tiibntick.core.agency.compliance.application.port.out.DisputeCorePort;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.IncidentCorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Compliance orchestration against platform incident/dispute cores.
 *
 * <p>Ported from the BFF {@code IncidentOrchestrator}. Enabled via
 * {@code tnt.agency.compliance.enabled} (default true).
 */
@Service
public class ComplianceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ComplianceOrchestrator.class);

    private final IncidentCorePort incidentCore;
    private final DisputeCorePort disputeCore;
    private final boolean enabled;

    public ComplianceOrchestrator(
            IncidentCorePort incidentCore,
            DisputeCorePort disputeCore,
            @Value("${tnt.agency.compliance.enabled:true}") boolean enabled) {
        this.incidentCore = incidentCore;
        this.disputeCore = disputeCore;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Reports a mission anomaly as a platform incident. Errors are swallowed so the caller's
     * mission operation is never broken by a compliance-side failure.
     */
    public Mono<IncidentCorePort.IncidentView> reportIncident(
            UUID tenantId, UUID agencyId, UUID missionId,
            UUID delivererId, String anomalyType, String description) {
        if (!enabled) {
            return Mono.empty();
        }
        UUID reporter = delivererId != null ? delivererId : agencyId;
        return incidentCore.reportIncident(new IncidentCorePort.ReportIncidentRequest(
                        tenantId, agencyId, missionId, anomalyType, description, reporter))
                .onErrorResume(e -> {
                    log.warn("[Compliance] incident report failed mission={}: {}", missionId, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<DisputeCorePort.DisputeView> openClaim(DisputeCorePort.OpenDisputeRequest request) {
        if (!enabled) {
            return Mono.empty();
        }
        return disputeCore.openDispute(request);
    }
}
