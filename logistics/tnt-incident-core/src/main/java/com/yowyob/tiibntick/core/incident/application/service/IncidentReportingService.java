package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.ReportDriverWithdrawalCommand;
import com.yowyob.tiibntick.core.incident.application.command.ReportIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import com.yowyob.tiibntick.core.incident.port.inbound.IReportDriverWithdrawalUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IReportIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service orchestrating incident creation and driver withdrawal reporting.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentReportingService implements IReportIncidentUseCase, IReportDriverWithdrawalUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    private final IMissionStatusPort missionStatusPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IncidentTriageService triageService;

    /**
     * Reports and acknowledges a new incident, pauses the mission and publishes the creation event.
     *
     * @param command the incident report command
     * @return the acknowledged incident
     */
    /**
     * Records a driver withdrawal as an incident, pauses the mission and notifies the agency.
     *
     * @param command the driver withdrawal command
     * @return the created incident
     */
    @Override
    public Mono<Incident> execute(ReportIncidentCommand command) {
        IncidentType type = command.getType();
        var category = triageService.deriveCategory(type);

        Incident incident = Incident.createWithFreelancerOrg(
                command.getTenantId(), command.getAgencyId(), command.getPlatform(),
                command.getMissionId(), category, type,
                command.getDescription(), command.getReportedByActorId(),
                command.getReportedByRole(), command.getAffectedParcelIds(),
                command.getResponsibleOrgId(), command.getResponsibleOrgType()
        );

        return incidentRepository.save(incident)
                .flatMap(saved -> acknowledgeAndLog(saved, command.getReportedByActorId(), command.getReportedByRole()))
                .flatMap(saved -> missionStatusPort.pauseMission(saved.getMissionId(), saved.getId()).thenReturn(saved))
                .flatMap(saved -> publishCreated(saved))
                .flatMap(saved -> notifyAgency(saved))
                .flatMap(saved -> writeBlockchain(saved));
    }

    @Override
    public Mono<Incident> execute(ReportDriverWithdrawalCommand command) {
        var category = triageService.deriveCategory(command.getWithdrawalType());

        Incident incident = Incident.create(
                command.getTenantId(), command.getAgencyId(), command.getPlatform(),
                command.getMissionId(), category, command.getWithdrawalType(),
                "Driver withdrawal: " + command.getJustification(),
                command.getDriverActorId(), command.getDriverRole(),
                command.getAffectedParcelIds()
        );

        return incidentRepository.save(incident)
                .flatMap(saved -> acknowledgeAndLog(saved, command.getDriverActorId(), command.getDriverRole()))
                .flatMap(saved -> missionStatusPort.pauseMission(saved.getMissionId(), saved.getId()).thenReturn(saved))
                .flatMap(saved -> publishCreated(saved))
                .flatMap(saved -> notifyAgency(saved))
                .flatMap(saved -> writeBlockchain(saved));
    }

    private Mono<Incident> acknowledgeAndLog(Incident incident, UUID actorId, ActorRole role) {
        Incident acked = incident.acknowledge();
        IncidentEventLog log = IncidentEventLog.of(acked.getId(), "INCIDENT_CREATED_AND_ACKNOWLEDGED",
                actorId, role, "{\"status\":\"ACKNOWLEDGED\"}");
        return incidentRepository.save(acked)
                .flatMap(saved -> eventLogRepository.save(log).thenReturn(saved));
    }

    private Mono<Incident> publishCreated(Incident incident) {
        var event = IncidentCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .referenceCode(incident.getReferenceCode())
                .tenantId(incident.getTenantId())
                .agencyId(incident.getAgencyId())
                .missionId(incident.getMissionId())
                .platform(incident.getSourcePlatform())
                .category(incident.getCategory())
                .type(incident.getType())
                .description(incident.getDescription())
                .reportedByActorId(incident.getReportedByActorId())
                .reportedByRole(incident.getReportedByRole())
                .affectedParcelIds(incident.getAffectedParcelIds())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }

    private Mono<Incident> notifyAgency(Incident incident) {
        return notificationPort.notifyAgency(
                incident.getAgencyId(),
                "New Incident: " + incident.getReferenceCode(),
                "Type: " + incident.getType() + " | Mission: " + incident.getMissionId(),
                "INCIDENT_CREATED",
                incident.getId()
        ).thenReturn(incident);
    }

    private Mono<Incident> writeBlockchain(Incident incident) {
        String chainRef = incident.isMultiParcelIncident()
                ? "INC-CHAIN-" + incident.getId()
                : "PARCEL-CHAIN";
        return blockchainAuditPort.writeIncidentEvent(
                incident.getId(), chainRef, "INCIDENT_CREATED",
                "{\"ref\":\"" + incident.getReferenceCode() + "\"}"
        ).thenReturn(incident);
    }
}
