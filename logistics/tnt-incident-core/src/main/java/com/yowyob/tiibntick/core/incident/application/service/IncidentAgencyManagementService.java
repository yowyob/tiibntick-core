package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.ResolveIncidentCommand;
import com.yowyob.tiibntick.core.incident.application.command.StartAgencyHandlingCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentAssignment;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IResolveIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IStartAgencyHandlingUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service handling agency-level manual incident management and resolution.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentAgencyManagementService implements IStartAgencyHandlingUseCase, IResolveIncidentUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    //private final IBlockchainAuditPort blockchainAuditPort;
    private final IMissionStatusPort missionStatusPort;
    //private final IPaymentFreezePort paymentFreezePort;
    private final IActorReputationPort actorReputationPort;
    //private final IMediaStoragePort mediaStoragePort;

    @Override
    public Mono<Incident> execute(StartAgencyHandlingCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    Incident handling = incident.startAgencyHandling();
                    IncidentAssignment assignment = command.getAssignedToActorId() != null
                            ? IncidentAssignment.createManual(incident.getId(),
                            command.getAssignedToActorId(), ActorRole.AGENCY_MANAGER,
                            command.getInitiatedByActorId(), command.getAgencyId())
                            : IncidentAssignment.createAuto(incident.getId(), command.getAgencyId());

                    IncidentEventLog log = IncidentEventLog.of(incident.getId(),
                            "AGENCY_HANDLING_STARTED", command.getInitiatedByActorId(),
                            ActorRole.AGENCY_MANAGER, "{\"agencyId\":\"" + command.getAgencyId() + "\"}");

                    return incidentRepository.save(handling)
                            .flatMap(saved -> eventLogRepository.save(log).thenReturn(saved))
                            .flatMap(saved -> notifyHandlingStarted(saved, command))
                            .flatMap(saved -> publishStatusChanged(saved));
                });
    }

    @Override
    public Mono<Incident> execute(ResolveIncidentCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    Incident resolved = incident.resolve(command.getResolutionMode());
                    return incidentRepository.save(resolved)
                            .flatMap(saved -> logResolution(saved, command))
                            .flatMap(saved -> updateReputationIfNeeded(saved))
                            .flatMap(saved -> resumeMissionIfNeeded(saved))
                            .flatMap(saved -> publishResolved(saved));
                });
    }

    private Mono<Incident> notifyHandlingStarted(Incident incident, StartAgencyHandlingCommand command) {
        return notificationPort.notifyAgency(command.getAgencyId(),
                "Incident under agency handling: " + incident.getReferenceCode(),
                "Assigned to: " + (command.getAssignedToActorId() != null ? command.getAssignedToActorId() : "agency"),
                "AGENCY_HANDLING_STARTED", incident.getId()
        ).thenReturn(incident);
    }

    private Mono<Incident> publishStatusChanged(Incident incident) {
        var event = IncidentStatusChangedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .agencyId(incident.getAgencyId())
                .platform(incident.getSourcePlatform())
                .previousStatus(null)
                .newStatus(incident.getStatus())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }

    private Mono<Incident> logResolution(Incident incident, ResolveIncidentCommand command) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_RESOLVED",
                command.getResolvedByActorId(), ActorRole.AGENCY_MANAGER,
                "{\"mode\":\"" + command.getResolutionMode() + "\",\"notes\":\"" + command.getResolutionNotes() + "\"}");
        return eventLogRepository.save(log).thenReturn(incident);
    }

    private Mono<Incident> updateReputationIfNeeded(Incident incident) {
        if (incident.getType().name().startsWith("DRIVER_") &&
                incident.getReportedByRole() != ActorRole.SYSTEM) {
            return actorReputationPort
                    .decreaseReputation(incident.getReportedByActorId(), 5.0, "INCIDENT: " + incident.getReferenceCode())
                    .thenReturn(incident);
        }
        return Mono.just(incident);
    }

    private Mono<Incident> resumeMissionIfNeeded(Incident incident) {
        return missionStatusPort.getMissionSnapshot(incident.getMissionId())
                .flatMap(snap -> missionStatusPort.resumeMission(incident.getMissionId(),
                        snap.currentDriverId(), snap.currentVehicleId()))
                .thenReturn(incident);
    }

    private Mono<Incident> publishResolved(Incident incident) {
        long duration = incident.getResolvedAt() != null && incident.getReportedAt() != null
                ? java.time.Duration.between(incident.getReportedAt(), incident.getResolvedAt()).toMinutes() : 0L;

        var event = IncidentResolvedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .agencyId(incident.getAgencyId())
                .platform(incident.getSourcePlatform())
                .resolutionMode(incident.getResolutionMode())
                .durationMinutes(duration)
                .slaBreached(incident.getSlaImpact() != null && incident.getSlaImpact().isSlaBreached())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }
}
