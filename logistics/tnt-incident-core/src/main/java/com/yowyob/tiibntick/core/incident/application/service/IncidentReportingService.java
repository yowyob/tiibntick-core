package com.yowyob.tiibntick.core.incident.application.service;

import java.util.Map;
import com.yowyob.tiibntick.core.incident.application.command.ReportDriverWithdrawalCommand;
import com.yowyob.tiibntick.core.incident.application.command.ReportIncidentCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentType;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.domain.valueobject.IncidentGeoSnapshot;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentBlockingPolicy;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.yowyob.tiibntick.core.incident.port.inbound.IReportDriverWithdrawalUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IReportIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final INotificationPort notificationPort;
    private final IDeliveryStatusPort deliveryStatusPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IncidentTriageService triageService;
    private final IncidentBlockingPolicy blockingPolicy;
    private final IncidentAutoResolutionService autoResolution;

    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "create")
    public Mono<Incident> execute(ReportIncidentCommand command) {
        IncidentType type = command.getType();
        Incident incident = Incident.createWithFreelancerOrg(
                command.getTenantId(), command.getAgencyId(), command.getPlatform(),
                command.getMissionId(), triageService.deriveCategory(type), type,
                command.getDescription(), command.getReportedByActorId(),
                command.getReportedByRole(), command.getAffectedParcelIds(),
                command.getResponsibleOrgId(), command.getResponsibleOrgType(),
                command.getTrackingCode());
        if (command.isBlockingForced()) {
            incident = incident.forceBlockingByCourier();
        }
        return declare(incident, command.getCurrentLat(), command.getCurrentLng());
    }

    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "create")
    public Mono<Incident> execute(ReportDriverWithdrawalCommand command) {
        Incident incident = Incident.create(
                command.getTenantId(), command.getAgencyId(), command.getPlatform(),
                command.getMissionId(), triageService.deriveCategory(command.getWithdrawalType()),
                command.getWithdrawalType(),
                "Driver withdrawal: " + command.getJustification(),
                command.getDriverActorId(), command.getDriverRole(),
                command.getAffectedParcelIds());
        return declare(incident, command.getCurrentLat(), command.getCurrentLng());
    }

    /**
     * Rows I1 → I2 → I3, then D13, D15 (blocking) or D14 (not blocking), then I4/I5 for a blocking incident.
     * Guards of I1: the type is declarable by a courier (QO-12) and the declarant is the delivery's assigned courier.
     */
    private Mono<Incident> declare(Incident reported, Double lat, Double lng) {
        if (reported.getType() == null || !reported.getType().isDeclarableByCourier()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Incident type " + reported.getType() + " cannot be declared by a courier"));
        }
        return deliveryStatusPort.isAssignedCourier(reported.getMissionId(), reported.getReportedByActorId())
                .flatMap(assigned -> assigned ? incidentRepository.save(reported)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the courier assigned to the delivery may declare an incident on it")))
                // D13 (the delivery adapter leaves a non-declarable state untouched: incident attached without move)
                .flatMap(saved -> deliveryStatusPort.markIncidentReported(saved.getMissionId(), saved.getId())
                        .thenReturn(saved))
                .flatMap(saved -> incidentRepository.save(acknowledgeAndTriage(saved, lat, lng)))
                .flatMap(this::log)
                .flatMap(saved -> (saved.isBlocking()
                        ? deliveryStatusPort.pauseDelivery(saved.getMissionId(), saved.getId())              // D15
                        : deliveryStatusPort.resumeDelivery(saved.getMissionId(),                           // D14
                        IDeliveryStatusPort.TRIGGER_INCIDENT_NOT_BLOCKING, null, null))
                        .thenReturn(saved))
                .flatMap(this::notifyAgency)
                .flatMap(this::writeBlockchain)
                .flatMap(saved -> saved.isBlocking() ? autoResolution.routeBlocking(saved) : Mono.just(saved));
    }

    /** I2 then I3: severity, geo-snapshot at the declaration position, {@code blocking} (QO-2). */
    private Incident acknowledgeAndTriage(Incident incident, Double lat, Double lng) {
        var severity = triageService.determineSeverity(incident.getType(), incident.getCategory());
        IncidentGeoSnapshot geo = lat == null || lng == null ? null : IncidentGeoSnapshot.builder()
                .incidentId(incident.getId())
                .latitude(lat).longitude(lng)
                .capturedAt(Instant.now())
                .zoneRiskIndex(0.3)
                .build();
        boolean blocking = blockingPolicy.isBlocking(incident.getType(), severity,
                incident.isBlockingForcedByCourier());
        return incident.acknowledge().triage(severity, geo, null, blocking);
    }

    private Mono<Incident> log(Incident incident) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_CREATED_AND_ACKNOWLEDGED",
                incident.getReportedByActorId(), incident.getReportedByRole(),
                "{\"status\":\"" + incident.getStatus() + "\",\"blocking\":" + incident.isBlocking() + "}");
        return eventLogRepository.save(log).thenReturn(incident);
    }


    private Mono<Incident> notifyAgency(Incident incident) {
        if (incident.getAgencyId() == null) {
            // GO/FREELANCER-platform incident with no owning agency — nothing to notify here;
            // FreelancerOrg-context routing (responsibleOrgId) is handled separately.
            return Mono.just(incident);
        }
        return notificationPort.notifyAgency(
                incident.getAgencyId(),
                "New Incident: " + incident.getReferenceCode(),
                "Type: " + incident.getType() + " | Mission: " + incident.getMissionId(),
                "INCIDENT_CREATED",
                incident.getId(),
                Map.of("reference", String.valueOf(incident.getReferenceCode()),
                        "incidentType", String.valueOf(incident.getType()),
                        "missionId", String.valueOf(incident.getMissionId()))
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
