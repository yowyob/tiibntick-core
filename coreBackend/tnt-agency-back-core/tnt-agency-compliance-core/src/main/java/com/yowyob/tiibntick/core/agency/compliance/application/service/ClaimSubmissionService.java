package com.yowyob.tiibntick.core.agency.compliance.application.service;

import com.yowyob.tiibntick.core.agency.compliance.application.port.out.ClaimEventPublisherPort;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.DisputeCorePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Submits a client claim: opens a dispute on the platform dispute-core (when enabled) and emits a
 * claim notification event. Ported from the BFF {@code ClientController.submitClaim} + orchestrator.
 */
@Service
public class ClaimSubmissionService {

    private final ComplianceOrchestrator orchestrator;
    private final ClaimEventPublisherPort claimPublisher;

    public ClaimSubmissionService(ComplianceOrchestrator orchestrator,
                                  ClaimEventPublisherPort claimPublisher) {
        this.orchestrator = orchestrator;
        this.claimPublisher = claimPublisher;
    }

    public Mono<ClaimResult> submit(UUID tenantId, UUID agencyId, UUID missionId,
                                    String claimType, String description, String contactEmail) {
        Mono<Optional<String>> disputeRef = orchestrator.openClaim(new DisputeCorePort.OpenDisputeRequest(
                        tenantId, agencyId, missionId, claimType, description, contactEmail))
                .map(view -> Optional.ofNullable(view.reference()))
                .onErrorReturn(Optional.empty())
                .defaultIfEmpty(Optional.empty());

        Mono<String> claimRef = claimPublisher.submitClaim(new ClaimEventPublisherPort.ClaimSubmission(
                tenantId, agencyId, missionId, claimType, description, contactEmail));

        return Mono.zip(disputeRef, claimRef)
                .map(tuple -> {
                    String reference = tuple.getT1().orElse(tuple.getT2());
                    return new ClaimResult(reference, "Réclamation enregistrée.");
                });
    }

    public record ClaimResult(String reference, String message) {}
}
