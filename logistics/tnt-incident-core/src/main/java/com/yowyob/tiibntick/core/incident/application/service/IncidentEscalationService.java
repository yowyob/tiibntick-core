package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.EscalateIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEscalation;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.ICancelIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.ICloseIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IEscalateIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service handling incident escalation, closure, cancellation and dispute triggering.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentEscalationService implements IEscalateIncidentUseCase,
        ICloseIncidentUseCase, ICancelIncidentUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    private final IActorReputationPort actorReputationPort;
    private final IMediaStoragePort mediaStoragePort;
    private final IPaymentFreezePort paymentFreezePort;
    private final IBlockchainAuditPort blockchainAuditPort;

    /**
     * Escalates the incident, optionally triggering an automated dispute in tnt-dispute-core.
     *
     * @param command the escalation command
     * @return the escalated incident
     */
    /**
     * Closes a resolved incident: writes blockchain closure block, archives evidence
     * and links parcel chains back.
     *
     * @param incidentId      the incident to close
     * @param closedByActorId the actor performing the closure
     * @return the closed incident
     */
    /**
     * Cancels an incident and unfreezes any frozen payment.
     *
     * @param incidentId          the incident to cancel
     * @param cancelledByActorId  the actor performing the cancellation
     * @param reason              cancellation reason
     * @return the cancelled incident
     */
    @Override
    public Mono<Incident> execute(EscalateIncidentCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    int level = incident.getLastEscalationLevel() + 1;
                    Incident escalated = incident.escalate(level, command.getTargetRole());

                    IncidentEscalation escalation = IncidentEscalation.create(
                            incident.getId(), level,
                            command.getEscalatedByActorId(), command.getEscalatedByRole(),
                            command.getTargetActorId(), command.getTargetRole(),
                            command.getReason());

                    return incidentRepository.save(escalated)
                            .flatMap(saved -> logEscalation(saved, command))
                            .flatMap(saved -> {
                                if (command.isTriggerDispute()) {
                                    return flagFraudAndTriggerDispute(saved, command);
                                }
                                return notifyEscalation(saved, level, command.getTargetActorId())
                                        .thenReturn(saved);
                            })
                            .flatMap(saved -> publishEscalated(saved, level));
                });
    }

    @Override
    public Mono<Incident> execute(UUID incidentId, UUID closedByActorId) {
        return incidentRepository.findById(incidentId)
                .flatMap(incident -> {
                    Incident closed = incident.close();
                    return incidentRepository.save(closed)
                            .flatMap(saved -> logClosure(saved, closedByActorId))
                            .flatMap(saved -> writeBlockchainClosure(saved))
                            .flatMap(saved -> linkParcelChains(saved))
                            .flatMap(saved -> archiveEvidence(saved))
                            .flatMap(saved -> publishClosed(saved));
                });
    }

    @Override
    public Mono<Incident> execute(UUID incidentId, UUID cancelledByActorId, String reason) {
        return incidentRepository.findById(incidentId)
                .flatMap(incident -> {
                    Incident cancelled = incident.cancel(reason);
                    return incidentRepository.save(cancelled)
                            .flatMap(saved -> {
                                IncidentEventLog log = IncidentEventLog.of(saved.getId(), "INCIDENT_CANCELLED",
                                        cancelledByActorId, ActorRole.SYSTEM,
                                        "{\"reason\":\"" + reason + "\"}");
                                return eventLogRepository.save(log).thenReturn(saved);
                            })
                            .flatMap(saved -> paymentFreezePort.unfreezePayment(saved.getMissionId(),
                                    "Incident cancelled: " + reason).thenReturn(saved))
                            .flatMap(saved -> publishCancelled(saved, reason));
                });
    }

    private Mono<Incident> flagFraudAndTriggerDispute(Incident incident, EscalateIncidentCommand cmd) {
        return actorReputationPort.flagForFraud(incident.getReportedByActorId(),
                incident.getId(), cmd.getFraudEvidence())
                .thenReturn(incident)
                .flatMap(saved -> {
                    var event = IncidentEscalatedToDisputeEvent.builder()
                            .eventId(UUID.randomUUID())
                            .incidentId(saved.getId())
                            .tenantId(saved.getTenantId())
                            .missionId(saved.getMissionId())
                            .agencyId(saved.getAgencyId())
                            .affectedParcelIds(saved.getAffectedParcelIds())
                            .fraudReason(cmd.getFraudEvidence())
                            .occurredAt(Instant.now())
                            .build();
                    return eventPublisher.publish(event).thenReturn(saved);
                });
    }

    private Mono<Incident> logEscalation(Incident incident, EscalateIncidentCommand command) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_ESCALATED",
                command.getEscalatedByActorId(), command.getEscalatedByRole(),
                "{\"level\":" + incident.getLastEscalationLevel() + ",\"reason\":\"" + command.getReason() + "\"}");
        return eventLogRepository.save(log).thenReturn(incident);
    }

    private Mono<Void> notifyEscalation(Incident incident, int level, UUID targetActorId) {
        String title = "Incident escalated (L" + level + "): " + incident.getReferenceCode();
        if (targetActorId != null) {
            return notificationPort.notifyActor(targetActorId, title,
                    "Severity: " + incident.getSeverity(), "INCIDENT_ESCALATED", incident.getId());
        }
        return notificationPort.notifyAgency(incident.getAgencyId(), title,
                "Severity: " + incident.getSeverity(), "INCIDENT_ESCALATED", incident.getId());
    }

    private Mono<Incident> publishEscalated(Incident incident, int level) {
        var event = IncidentEscalatedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .agencyId(incident.getAgencyId())
                .escalationLevel(level)
                .escalatedToRole(ActorRole.AGENCY_MANAGER)
                .severity(incident.getSeverity())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }

    private Mono<Incident> logClosure(Incident incident, UUID closedBy) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_CLOSED",
                closedBy, ActorRole.SYSTEM, "{\"closedAt\":\"" + incident.getClosedAt() + "\"}");
        return eventLogRepository.save(log).thenReturn(incident);
    }

    private Mono<Incident> writeBlockchainClosure(Incident incident) {
        return blockchainAuditPort.writeIncidentEvent(
                incident.getId(),
                incident.getOwnBlockchainChainId() != null
                        ? incident.getOwnBlockchainChainId()
                        : "MISSION-" + incident.getMissionId(),
                "INCIDENT_CLOSED",
                "{\"ref\":\"" + incident.getReferenceCode() + "\"}"
        ).thenReturn(incident);
    }

    private Mono<Incident> linkParcelChains(Incident incident) {
        if (!incident.isMultiParcelIncident()) {
            return Mono.just(incident);
        }
        return Mono.just(incident);
    }

    private Mono<Incident> archiveEvidence(Incident incident) {
        return mediaStoragePort.archiveIncidentEvidence(incident.getTenantId(), incident.getId()).thenReturn(incident);
    }

    private Mono<Incident> publishClosed(Incident incident) {
        var event = IncidentClosedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .agencyId(incident.getAgencyId())
                .platform(incident.getSourcePlatform())
                .affectedParcelIds(incident.getAffectedParcelIds())
                .multiParcel(incident.isMultiParcelIncident())
                .incidentBlockchainChainId(incident.getOwnBlockchainChainId())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }

    private Mono<Incident> publishCancelled(Incident incident, String reason) {
        var event = IncidentCancelledEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .reason(reason)
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }
}
