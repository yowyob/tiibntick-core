package com.yowyob.tiibntick.core.incident.application.service;

import java.util.Map;
import com.yowyob.tiibntick.core.incident.application.command.AssignReplacementDriverCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.domain.valueobject.PricingAdjustment;
import com.yowyob.tiibntick.core.incident.port.inbound.IAssignReplacementDriverUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IStartAutoResolutionUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import com.yowyob.tiibntick.core.incident.config.IncidentLifecycleProperties;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
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
    private final INotificationPort notificationPort;
    private final IPaymentFreezePort paymentFreezePort;
    private final IDriverAvailabilityPort driverAvailabilityPort;
    private final IVehicleCompatibilityPort vehicleCompatibilityPort;
    private final IRouteOptimizerPort routeOptimizerPort;
    private final IDeliveryStatusPort deliveryStatusPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IncidentResolutionEffects resolutionEffects;
    private final IncidentLifecycleProperties lifecycle;

    /** Manual (re)start of the routing of a triaged blocking incident (support tooling). */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<Incident> execute(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + incidentId)))
                .flatMap(this::routeBlocking);
    }

    /**
     * Rows I4/I5, called right after the triage of a blocking incident: the payment is frozen, then an agency
     * delivery waits for its agency (I5, [D.4]) and any other delivery goes to the automatic engine (I4).
     */
    public Mono<Incident> routeBlocking(Incident triaged) {
        if (triaged.getStatus() != IncidentStatus.TRIAGED || !triaged.isBlocking()) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a triaged blocking incident can be routed; incident " + triaged.getId() + " is "
                            + triaged.getStatus() + (triaged.isBlocking() ? "" : " and not blocking")));
        }
        Mono<Incident> frozen = paymentFreezePort
                .freezePayment(triaged.getMissionId(), "INCIDENT: " + triaged.getReferenceCode())
                .thenReturn(triaged);
        if (triaged.getAgencyId() != null) {
            return frozen.flatMap(inc -> incidentRepository.save(inc.pendingAgencyAssignment()))
                    .flatMap(this::notifyManager);
        }
        return frozen.flatMap(inc -> incidentRepository.save(inc.startAutoResolution()))
                .flatMap(this::runEngine);
    }

    /** I7 (courier replacement), I8 (GEO_* rerouting), otherwise I10 → I11 (the support takes over). */
    private Mono<Incident> runEngine(Incident resolving) {
        if (resolving.requiresDriverReplacement()) {
            return incidentRepository.save(resolving.startReassigningDriver())
                    .flatMap(this::findAndProposeBestDriver);
        }
        if (resolving.getType() != null && resolving.getType().name().startsWith("GEO_")) {
            return startRerouting(resolving);
        }
        return failToManager(resolving, "no automatic resolution for type " + resolving.getType());
    }

    private Mono<Incident> findAndProposeBestDriver(Incident incident) {
        double lat = incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLatitude() : 0;
        double lng = incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLongitude() : 0;

        return driverAvailabilityPort
                .findEligibleReplacementDrivers(incident.getTenantId(), incident.getAgencyId(),
                        lat, lng, 10.0, "ANY") // required capacity kg (unchanged)
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
                    // the proposed courier accepts with POST /api/v1/incidents/{id}/replacement/accept (I12/I16)
                    return replacementRepository.save(replacement)
                            .flatMap(rep -> notifyDriverProposal(incident, rep))
                            .thenReturn(incident);
                })
                .switchIfEmpty(Mono.defer(() -> transferToHubOrFail(incident, lat, lng)));
    }

    /** I9 (provisional QO-13): no replacement, a relay hub in range takes the parcels; otherwise I10 → I11. */
    private Mono<Incident> transferToHubOrFail(Incident incident, double lat, double lng) {
        if (incident.getGeoSnapshot() == null) {
            return failToManager(incident, "no replacement courier available");
        }
        return routeOptimizerPort.findNearestHub(lat, lng, incident.getTenantId())
                .filter(hub -> hub.distanceKm() <= lifecycle.getHubTransferMaxKm())
                .flatMap(hub -> incidentRepository.save(incident.startTransferringToHub())
                        .flatMap(saved -> notificationPort.notifyActor(saved.getReportedByActorId(),
                                "Drop the parcels at the relay hub",
                                "No replacement courier: bring the parcels to " + hub.hubName()
                                        + " (" + String.format(java.util.Locale.ROOT, "%.1f", hub.distanceKm()) + " km)",
                                "INCIDENT_TRANSFER_TO_HUB", saved.getId(),
                                Map.of("reference", String.valueOf(saved.getReferenceCode()),
                                        "hubId", String.valueOf(hub.hubId()),
                                        "hubName", String.valueOf(hub.hubName()))).thenReturn(saved)))
                .switchIfEmpty(Mono.defer(() -> failToManager(incident, "no replacement courier available")));
    }

    /** I10 then I11: the manager (the agency, else the TiiBnTick support) takes over. */
    private Mono<Incident> failToManager(Incident incident, String why) {
        return incidentRepository.save(incident.markAutoResolutionFailed().pendingAgencyAssignment())
                .flatMap(saved -> eventLogRepository.save(IncidentEventLog.of(saved.getId(),
                        "AUTO_RESOLUTION_FAILED", null, ActorRole.SYSTEM,
                        "{\"reason\":\"" + why.replace("\"", "'") + "\"}")).thenReturn(saved))
                .flatMap(this::notifyManager);
    }

    private Mono<Incident> notifyManager(Incident incident) {
        if (incident.getAgencyId() == null) {
            return Mono.just(incident); // TiiBnTick support: listed by the admin platform (PENDING_AGENCY_ASSIGNMENT)
        }
        return notificationPort.notifyAgency(incident.getAgencyId(),
                "Incident to handle: " + incident.getReferenceCode(),
                "A blocking incident is waiting for your handling.",
                "INCIDENT_AUTO_FAILED", incident.getId(),
                Map.of("reference", String.valueOf(incident.getReferenceCode()))).thenReturn(incident);
    }

    private Mono<Void> notifyDriverProposal(Incident incident, IncidentDriverReplacement rep) {
        return notificationPort.notifyActor(rep.getReplacementDriverId(),
                "New delivery assignment",
                "Emergency replacement needed for incident " + incident.getReferenceCode(),
                "INCIDENT_DRIVER_PROPOSAL", incident.getId(),
                Map.of("reference", String.valueOf(incident.getReferenceCode())));
    }

    /** I8 then I17 (rerouting applied, delivery resumed D16), or I10 → I11 when the rerouting fails. */
    private Mono<Incident> startRerouting(Incident incident) {
        if (incident.getGeoSnapshot() == null) {
            return failToManager(incident, "no position to reroute from");
        }
        return incidentRepository.save(incident.startRerouting())
                .flatMap(rerouting -> routeOptimizerPort.rerouteFromCurrentPosition(
                                rerouting.getMissionId(),
                                rerouting.getGeoSnapshot().getLatitude(),
                                rerouting.getGeoSnapshot().getLongitude())
                        .map(java.util.Optional::of)
                        .defaultIfEmpty(java.util.Optional.empty())
                        // only the routing call may fail into I10; a failure after the resolution propagates
                        .onErrorResume(e -> Mono.just(java.util.Optional.empty()))
                        .flatMap(result -> result.isEmpty()
                                ? failToManager(rerouting, "rerouting failed")
                                : incidentRepository.save(rerouting.resolve(ResolutionMode.FULLY_AUTOMATIC,
                                        IncidentDeliveryOutcome.RESUMED, IncidentResponsibleParty.NONE,
                                        "Rerouted, new ETA " + result.get().newEtaMinutes() + " min",
                                        "REROUTE_APPLIED", TntEventActor.system()))
                                .flatMap(saved -> resolutionEffects.afterResolved(saved, null, null))));
    }

    /**
     * I13: the agency designates one of its couriers (from AGENCY_HANDLING), or the manager forces the proposed one
     * (from REASSIGNING_DRIVER).
     */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<IncidentDriverReplacement> execute(AssignReplacementDriverCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> replacementRepository.findReplacementByIncidentId(incident.getId())
                        .defaultIfEmpty(IncidentDriverReplacement.initiate(incident.getId(),
                                incident.getReportedByActorId(), null,
                                incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLatitude() : 0,
                                incident.getGeoSnapshot() != null ? incident.getGeoSnapshot().getLongitude() : 0,
                                "Current position"))
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
                        .flatMap(rep -> applyReplacement(incident, rep, TntEventActor.of(
                                incident.getAgencyId() != null ? TntEventActor.Type.AGENCY : TntEventActor.Type.SUPPORT,
                                command.getAssignedByActorId())).thenReturn(rep)));
    }

    /** I12/I16: the proposed replacement courier accepts (the first one, [D.4]). */
    // permission checked by the controller (incident:create)
    @Transactional
    public Mono<IncidentDriverReplacement> acceptReplacement(UUID incidentId, UUID actorId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> replacementRepository.findReplacementByIncidentId(incidentId)
                        .filter(rep -> actorId != null && actorId.equals(rep.getReplacementDriverId()))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only the proposed replacement courier may accept")))
                        .flatMap(rep -> applyReplacement(incident, rep,
                                TntEventActor.of(TntEventActor.Type.COURIER, actorId)).thenReturn(rep)));
    }

    /**
     * The courier holds the parcel → hand-over (I12/I13, D17); otherwise the replacement takes the delivery at once
     * (I16, D16 with the new courier).
     */
    private Mono<Incident> applyReplacement(Incident incident, IncidentDriverReplacement rep, TntEventActor actor) {
        return deliveryStatusPort.courierHoldsParcel(incident.getMissionId())
                .flatMap(holds -> holds
                        ? replacementRepository.save(rep.toBuilder().handoverScheduledAt(Instant.now()).build())
                        .then(incidentRepository.save(incident.startAwaitingHandover()))
                        .flatMap(saved -> deliveryStatusPort.startHandover(saved.getMissionId()).thenReturn(saved))
                        : incidentRepository.save(incident.resolve(ResolutionMode.FULLY_AUTOMATIC,
                                IncidentDeliveryOutcome.RESUMED, IncidentResponsibleParty.NONE,
                                "Replacement courier before pickup", "REPLACEMENT_ASSIGNED", actor))
                        .flatMap(saved -> resolutionEffects.afterResolved(saved, rep.getReplacementDriverId(),
                                rep.getReplacementVehicleId())));
    }

}
