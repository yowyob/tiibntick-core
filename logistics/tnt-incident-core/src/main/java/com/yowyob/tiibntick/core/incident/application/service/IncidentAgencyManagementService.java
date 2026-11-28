package com.yowyob.tiibntick.core.incident.application.service;

import java.util.Map;
import com.yowyob.tiibntick.core.incident.application.command.ResolveIncidentCommand;
import com.yowyob.tiibntick.core.incident.application.command.StartAgencyHandlingCommand;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.incident.config.IncidentLifecycleProperties;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.port.inbound.IResolutionConfirmationUseCase;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentAssignment;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IResolveIncidentUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IStartAgencyHandlingUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class IncidentAgencyManagementService implements IStartAgencyHandlingUseCase, IResolveIncidentUseCase,
        IResolutionConfirmationUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final INotificationPort notificationPort;
    private final IDeliveryStatusPort deliveryStatusPort;
    private final IActorReputationPort actorReputationPort;
    private final IncidentResolutionEffects resolutionEffects;
    private final IncidentLifecycleProperties lifecycle;

    /** I18 (PENDING_AGENCY_ASSIGNMENT), I21 (cooperation refused/failed), I28 (taken back after escalation). */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<Incident> execute(StartAgencyHandlingCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    TntEventActor manager = incident.managerActor(command.getInitiatedByActorId());
                    String trigger = switch (incident.getStatus()) {
                        case ESCALATED -> "TAKEN_BACK_AFTER_ESCALATION";
                        case WAITING_INTERAGENCY, INTERAGENCY_IN_PROGRESS -> "COOPERATION_ENDED";
                        default -> "START_HANDLING";
                    };
                    Incident handling = transition(() -> incident.startAgencyHandling(manager, trigger));
                    if (command.getAssignedToActorId() != null) {
                        handling = handling.recordHandlerAssigned(command.getAssignedToActorId(), manager);
                    }
                    IncidentEventLog log = IncidentEventLog.of(incident.getId(),
                            "AGENCY_HANDLING_STARTED", command.getInitiatedByActorId(),
                            command.getAgencyId() != null ? ActorRole.AGENCY_MANAGER : ActorRole.SUPPORT_AGENT,
                            command.getAgencyId() != null ? "{\"agencyId\":\"" + command.getAgencyId() + "\"}" : "{}");

                    return incidentRepository.save(handling)
                            .flatMap(saved -> eventLogRepository.save(log).thenReturn(saved))
                            .flatMap(saved -> notifyHandlingStarted(saved, command));
                });
    }

    /**
     * I6, I17 (manual), I22, I26, I29: the manager resolves, with the delivery outcome (mandatory for a blocking
     * incident, RESUMED by default otherwise) and the responsible party; the effects follow (D16/D20, unfreeze).
     */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<Incident> execute(ResolveIncidentCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    IncidentDeliveryOutcome outcome = command.getDeliveryOutcome() != null
                            ? command.getDeliveryOutcome()
                            : incident.isBlocking() ? null : IncidentDeliveryOutcome.RESUMED;
                    if (outcome == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "deliveryOutcome (RESUMED or FAILED) is mandatory to resolve a blocking incident"));
                    }
                    Incident resolved = transition(() -> incident.resolve(command.getResolutionMode(), outcome,
                            command.getResponsibleParty(), command.getResolutionNotes(), "RESOLVE",
                            incident.managerActor(command.getResolvedByActorId())));
                    return finishResolution(resolved, command.getResolvedByActorId(),
                            String.valueOf(command.getResolutionMode()), command.getResolutionNotes());
                });
    }

    // permission checked by the controller (incident:manage / incident:create): no new application → web dependency
    @Override
    @Transactional
    public Mono<Incident> propose(UUID incidentId, UUID managerActorId, IncidentDeliveryOutcome outcome,
                                  IncidentResponsibleParty responsibleParty, String note) {
        if (outcome == null || outcome == IncidentDeliveryOutcome.DISPUTE) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "deliveryOutcome must be RESUMED or FAILED"));
        }
        return load(incidentId)
                .flatMap(incident -> incidentRepository.save(transition(() -> incident.proposeResolution(outcome,
                        responsibleParty, note, incident.managerActor(managerActorId)))))
                .flatMap(saved -> notificationPort.notifyActor(saved.getReportedByActorId(),
                        "Resolution proposed: " + saved.getReferenceCode(),
                        "Confirm or contest the proposed resolution.",
                        "INCIDENT_RESOLUTION_PROPOSED", saved.getId(),
                        Map.of("reference", String.valueOf(saved.getReferenceCode()),
                                "deliveryOutcome", outcome.name())).thenReturn(saved));
    }

    // permission checked by the controller (incident:manage / incident:create): no new application → web dependency
    @Override
    @Transactional
    public Mono<Incident> confirm(UUID incidentId, UUID courierActorId) {
        return loadOwn(incidentId, courierActorId)
                .flatMap(incident -> finishResolution(transition(() -> incident.confirmResolution(
                                Incident.actorOf(incident.getReportedByRole(), courierActorId), false)),
                        courierActorId, "CONFIRMED", null));
    }

    // permission checked by the controller (incident:manage / incident:create): no new application → web dependency
    @Override
    @Transactional
    public Mono<Incident> contest(UUID incidentId, UUID courierActorId, String reason) {
        return loadOwn(incidentId, courierActorId)
                .flatMap(incident -> incidentRepository.save(transition(() -> incident.contestResolution(reason,
                        Incident.actorOf(incident.getReportedByRole(), courierActorId)))))
                .flatMap(saved -> saved.getAgencyId() == null ? Mono.just(saved)
                        : notificationPort.notifyAgency(saved.getAgencyId(),
                        "Resolution contested: " + saved.getReferenceCode(), String.valueOf(reason),
                        "INCIDENT_RESOLUTION_CONTESTED", saved.getId(),
                        Map.of("reference", String.valueOf(saved.getReferenceCode()))).thenReturn(saved));
    }

    // permission checked by the controller (incident:manage / incident:create): no new application → web dependency
    @Override
    @Transactional
    public Mono<Incident> resolveOwnNonBlocking(UUID incidentId, UUID courierActorId, String note) {
        return loadOwn(incidentId, courierActorId)
                .flatMap(incident -> {
                    if (incident.isBlocking() || incident.getStatus() != IncidentStatus.TRIAGED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "A courier may only close his own non-blocking triaged incident"));
                    }
                    return finishResolution(incident.resolve(ResolutionMode.FULLY_AUTOMATIC,
                                    IncidentDeliveryOutcome.RESUMED, IncidentResponsibleParty.NONE, note,
                                    "CLOSED_BY_COURIER", Incident.actorOf(incident.getReportedByRole(), courierActorId)),
                            courierActorId, "CLOSED_BY_COURIER", note);
                });
    }

    /** Called by the lifecycle job (system, no caller permission). */
    @Override
    public Mono<Long> confirmExpired(Instant now) {
        return incidentRepository.findConfirmationExpired(now.minus(lifecycle.getConfirmationDelay()),
                        lifecycle.getBatchSize())
                .concatMap(incident -> finishResolution(incident.confirmResolution(TntEventActor.system(), true),
                        null, "CONFIRMATION_TIMED_OUT", null))
                .count();
    }

    private Mono<Incident> finishResolution(Incident resolved, UUID byActorId, String mode, String notes) {
        return incidentRepository.save(resolved)
                .flatMap(saved -> logResolution(saved, byActorId, mode, notes))
                .flatMap(this::updateReputationIfNeeded)
                .flatMap(saved -> resolutionEffects.afterResolved(saved, null, null));
    }

    private Mono<Incident> load(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")));
    }

    /** The declaring courier only. */
    private Mono<Incident> loadOwn(UUID incidentId, UUID courierActorId) {
        return load(incidentId).flatMap(incident -> courierActorId != null
                && courierActorId.equals(incident.getReportedByActorId())
                ? Mono.just(incident)
                : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the courier who declared the incident may do this")));
    }

    /** A move refused by the transition table (spec §1.3) is a 409 for the caller. */
    static Incident transition(java.util.function.Supplier<Incident> move) {
        try {
            return move.get();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private Mono<Incident> notifyHandlingStarted(Incident incident, StartAgencyHandlingCommand command) {
        if (command.getAgencyId() == null) {
            return Mono.just(incident); // the TiiBnTick support took it: no agency to notify
        }
        return notificationPort.notifyAgency(command.getAgencyId(),
                "Incident under agency handling: " + incident.getReferenceCode(),
                "Assigned to: " + (command.getAssignedToActorId() != null ? command.getAssignedToActorId() : "agency"),
                "AGENCY_HANDLING_STARTED", incident.getId(),
                Map.of("reference", String.valueOf(incident.getReferenceCode()),
                        "assignedTo", command.getAssignedToActorId() != null
                                ? command.getAssignedToActorId().toString() : "")
        ).thenReturn(incident);
    }


    private Mono<Incident> logResolution(Incident incident, UUID byActorId, String mode, String notes) {
        IncidentEventLog log = IncidentEventLog.of(incident.getId(), "INCIDENT_RESOLVED",
                byActorId, ActorRole.AGENCY_MANAGER,
                "{\"mode\":\"" + mode + "\",\"deliveryOutcome\":\"" + incident.getDeliveryOutcome()
                        + "\",\"notes\":\"" + (notes == null ? "" : notes.replace("\"", "'")) + "\"}");
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

}
