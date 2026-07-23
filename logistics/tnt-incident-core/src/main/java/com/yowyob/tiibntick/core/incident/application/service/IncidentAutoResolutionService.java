package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.AssignReplacementDriverCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.domain.valueobject.PricingAdjustment;
import com.yowyob.tiibntick.core.incident.port.inbound.IAssignReplacementDriverUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IStartAutoResolutionUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application service driving automatic resolution: driver reassignment, rerouting and escalation on failure.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentAutoResolutionService implements IStartAutoResolutionUseCase, IAssignReplacementDriverUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentDriverReplacementRepository replacementRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    private final IPaymentFreezePort paymentFreezePort;
    private final IDriverAvailabilityPort driverAvailabilityPort;
    private final IVehicleCompatibilityPort vehicleCompatibilityPort;
    private final IRouteOptimizerPort routeOptimizerPort;
    private final IMissionStatusPort missionStatusPort;
    private final IBlockchainAuditPort blockchainAuditPort;

    /**
     * Starts the automatic resolution process: freezes payment, then either
     * reassigns the driver or reroutes the mission.
     *
     * @param incidentId the incident to auto-resolve
     * @return the incident in AUTO_RESOLVING or a sub-state
     */
    /**
     * Manually assigns a replacement driver and transitions the incident to AWAITING_HANDOVER.
     *
     * @param command the assignment command
     * @return the driver replacement record
     */
    @Override
    @Transactional
    public Mono<Incident> execute(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)))
                .flatMap(incident -> {
                    Incident resolving = incident.startAutoResolution();
                    return incidentRepository.save(resolving)
                            .flatMap(this::freezePaymentAndRoute);
                });
    }

    private Mono<Incident> freezePaymentAndRoute(Incident incident) {
        return paymentFreezePort.freezePayment(incident.getMissionId(), "INCIDENT: " + incident.getReferenceCode())
                .thenReturn(incident)
                .flatMap(inc -> {
                    if (inc.requiresDriverReplacement()) {
                        return startDriverReassignment(inc);
                    }
                    return startRerouting(inc);
                });
    }

    private Mono<Incident> startDriverReassignment(Incident incident) {
        Incident reassigning = incident.startReassigningDriver();
        return incidentRepository.save(reassigning)
                .flatMap(saved -> findAndProposeBestDriver(saved))
                .flatMap(saved -> publishStatusChanged(saved, IncidentStatus.AUTO_RESOLVING, IncidentStatus.REASSIGNING_DRIVER));
    }

    private Mono<Incident> findAndProposeBestDriver(Incident incident) {
        double lat = incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLatitude() : 0;
        double lng = incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLongitude() : 0;

        return driverAvailabilityPort
                .findEligibleReplacementDrivers(incident.getTenantId(), incident.getAgencyId(),
                        lat, lng, 10.0, "ANY")
                .take(1)
                .singleOrEmpty()
                .flatMap(candidate -> {
                    IncidentDriverReplacement replacement = IncidentDriverReplacement
                            .initiate(incident.getId(), incident.getReportedByActorId(),
                                    null, lat, lng, "Current position")
                            .assignReplacement(candidate.driverId(), candidate.vehicleId(),
                                    candidate.agencyId(),
                                    PricingAdjustment.builder()
                                            .originalPriceXAF(BigDecimal.ZERO)
                                            .adjustedPriceXAF(BigDecimal.ZERO)
                                            .extraKmFee(BigDecimal.valueOf(candidate.distanceKm() * 50))
                                            .urgencyFee(BigDecimal.valueOf(500))
                                            .adjustmentReason("Incident reassignment")
                                            .build());
                    return replacementRepository.save(replacement)
                            .flatMap(rep -> notifyDriverProposal(incident, rep))
                            .thenReturn(incident);
                })
                .switchIfEmpty(handleNoDriverAvailable(incident));
    }

    private Mono<Void> notifyDriverProposal(Incident incident, IncidentDriverReplacement rep) {
        return notificationPort.notifyActor(rep.getReplacementDriverId(),
                "New delivery assignment",
                "Emergency replacement needed for incident " + incident.getReferenceCode(),
                "INCIDENT_DRIVER_PROPOSAL", incident.getId());
    }

    private Mono<Incident> handleNoDriverAvailable(Incident incident) {
        Incident failed = incident.markAutoResolutionFailed().pendingAgencyAssignment();
        return incidentRepository.save(failed)
                .flatMap(saved -> notificationPort.notifyAgency(saved.getAgencyId(),
                        "Auto-resolution failed: " + saved.getReferenceCode(),
                        "No replacement driver found. Manual intervention required.",
                        "INCIDENT_AUTO_FAILED", saved.getId()).thenReturn(saved));
    }

    private Mono<Incident> startRerouting(Incident incident) {
        Incident rerouting = incident.startRerouting();
        return incidentRepository.save(rerouting)
                .flatMap(saved -> routeOptimizerPort.rerouteFromCurrentPosition(
                        saved.getMissionId(),
                        saved.getGeoSnapshot() != null ? saved.getGeoSnapshot().getLatitude() : 0,
                        saved.getGeoSnapshot() != null ? saved.getGeoSnapshot().getLongitude() : 0)
                        .thenReturn(saved))
                .flatMap(saved -> publishStatusChanged(saved, IncidentStatus.AUTO_RESOLVING, IncidentStatus.REROUTING));
    }

    @Override
    @Transactional
    public Mono<IncidentDriverReplacement> execute(AssignReplacementDriverCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .flatMap(incident -> replacementRepository.findReplacementByIncidentId(incident.getId())
                        .map(rep -> rep.assignReplacement(command.getReplacementDriverId(),
                                command.getReplacementVehicleId(),
                                command.getReplacementAgencyId(),
                                PricingAdjustment.builder()
                                        .originalPriceXAF(BigDecimal.ZERO)
                                        .adjustedPriceXAF(BigDecimal.ZERO)
                                        .extraKmFee(BigDecimal.ZERO)
                                        .urgencyFee(BigDecimal.valueOf(500))
                                        .adjustmentReason("Manual incident assignment")
                                        .build()))
                        .flatMap(replacementRepository::save)
                        .flatMap(rep -> {
                            Incident awaiting = incident.startAwaitingHandover();
                            return incidentRepository.save(awaiting)
                                    .flatMap(saved -> publishDriverAssigned(saved, rep))
                                    .thenReturn(rep);
                        }));
    }

    private Mono<Incident> publishDriverAssigned(Incident incident, IncidentDriverReplacement rep) {
        var event = IncidentDriverAssignedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .originalDriverId(rep.getOriginalDriverId())
                .replacementDriverId(rep.getReplacementDriverId())
                .replacementVehicleId(rep.getReplacementVehicleId())
                .replacementAgencyId(rep.getReplacementAgencyId())
                .handoverLat(rep.getHandoverLatitude())
                .handoverLng(rep.getHandoverLongitude())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }

    private Mono<Incident> publishStatusChanged(Incident incident, IncidentStatus prev, IncidentStatus next) {
        var event = IncidentStatusChangedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .agencyId(incident.getAgencyId())
                .platform(incident.getSourcePlatform())
                .previousStatus(prev)
                .newStatus(next)
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(incident);
    }
}
