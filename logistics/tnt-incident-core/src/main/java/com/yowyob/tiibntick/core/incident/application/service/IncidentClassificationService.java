package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.TriageIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentRiskScoringService;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentGeoSnapshot;
import com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentSlaImpact;
import com.yowyob.tiibntick.core.incident.port.inbound.ITriageIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service orchestrating incident triage: severity assessment, risk scoring and blockchain chain initialization.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentClassificationService implements ITriageIncidentUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final IRouteOptimizerPort routeOptimizerPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IncidentTriageService triageService;
    private final IncidentRiskScoringService riskScoringService;

    /**
     * Executes the full triage pipeline: severity, geo-snapshot, SLA assessment,
     * risk scoring and optional blockchain chain initialization for multi-parcel incidents.
     *
     * @param command the triage command
     * @return the triaged and scored incident
     */
    @Override
    @Transactional
    public Mono<Incident> execute(TriageIncidentCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + command.getIncidentId())))
                .flatMap(incident -> performTriage(incident, command));
    }

    private Mono<Incident> performTriage(Incident incident, TriageIncidentCommand command) {
        var severity = triageService.determineSeverity(incident.getType(), incident.getCategory());

        IncidentSlaImpact slaImpact = command.getSlaDeadlineEpochSeconds() != null
                ? IncidentSlaImpact.compute(incident.getId(),
                Instant.ofEpochSecond(command.getSlaDeadlineEpochSeconds()), 0L)
                : null;

        return resolveGeoSnapshot(incident)
                .map(geo -> incident.triage(severity, geo, slaImpact))
                .map(triaged -> {
                    var score = riskScoringService.compute(triaged,
                            command.getDriverReputationScore(),
                            command.getParcelValueNormalized(),
                            command.getZoneDangerIndex(),
                            command.getCargoSensitivity(),
                            command.getWeatherIndex(),
                            command.getDriverIncidentHistory(),
                            command.getMissionComplexity());
                    return triaged.withRiskScore(score);
                })
                .flatMap(incidentRepository::save)
                .flatMap(saved -> logAndPublish(saved, command.getTriggeredByActorId()))
                .flatMap(saved -> initBlockchainChainIfMultiParcel(saved));
    }

    private Mono<IncidentGeoSnapshot> resolveGeoSnapshot(Incident incident) {
        if (incident.getGeoSnapshot() != null) {
            return Mono.just(incident.getGeoSnapshot());
        }
        return Mono.just(IncidentGeoSnapshot.builder()
                .incidentId(incident.getId())
                .latitude(0.0).longitude(0.0)
                .capturedAt(Instant.now())
                .zoneRiskIndex(0.3)
                .build());
    }

    private Mono<Incident> logAndPublish(Incident incident, UUID triggeredBy) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_TRIAGED",
                triggeredBy, null, "{\"severity\":\"" + incident.getSeverity() + "\"}");

        var event = IncidentTriagedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .severity(incident.getSeverity())
                .category(incident.getCategory())
                .riskScore(incident.getRiskScore() != null ? incident.getRiskScore().getGlobalScore() : 0.0)
                .autoResolutionRecommended(incident.getRiskScore() != null
                        && incident.getRiskScore().isAutoResolutionRecommended())
                .occurredAt(Instant.now())
                .build();

        return eventLogRepository.save(log)
                .then(eventPublisher.publish(event))
                .thenReturn(incident);
    }

    private Mono<Incident> initBlockchainChainIfMultiParcel(Incident incident) {
        if (!incident.requiresOwnBlockchainChain()) {
            return Mono.just(incident);
        }
        String chainId = "INC-" + incident.getId().toString().replace("-", "").substring(0, 16).toUpperCase();
        return blockchainAuditPort.writeIncidentEvent(
                incident.getId(), chainId, "INCIDENT_CHAIN_INITIALIZED",
                "{\"parcelCount\":" + incident.getAffectedParcelIds().size() + "}"
        ).flatMap(txHash -> {
            Incident withChain = incident.assignBlockchainChain(chainId);
            return incidentRepository.save(withChain);
        });
    }
}
