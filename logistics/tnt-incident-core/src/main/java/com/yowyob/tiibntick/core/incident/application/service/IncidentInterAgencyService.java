package com.yowyob.tiibntick.core.incident.application.service;

import java.util.Map;
import com.yowyob.tiibntick.core.incident.application.command.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.port.inbound.IInterAgencyCooperationUseCase;
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
 * Application service orchestrating inter-agency cooperation: request, accept, reject and completion.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentInterAgencyService implements IInterAgencyCooperationUseCase {

    private final IIncidentCooperationRepository cooperationRepository;
    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final INotificationPort notificationPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IncidentResolutionEffects resolutionEffects;

    /**
     * Creates a new inter-agency cooperation request and notifies the responding agency.
     *
     * @param command the cooperation request command
     * @return the created cooperation record
     */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<IncidentInterAgencyCooperation> request(RequestCooperationCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    if (incident.getAgencyId() == null) { // I19 guard: agency delivery only
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Inter-agency cooperation is only for an agency delivery"));
                    }
                    Incident waiting = IncidentAgencyManagementService.transition(incident::waitForInterAgency);
                    IncidentInterAgencyCooperation coop = IncidentInterAgencyCooperation.request(
                            command.getIncidentId(), command.getRequestingAgencyId(),
                            command.getRespondingAgencyId(), command.getCooperationType(),
                            command.getDetails());
                    return cooperationRepository.save(coop)
                            .flatMap(saved -> {
                                return incidentRepository.save(waiting)
                                        .then(notifyRespondingAgency(saved))
                                        .thenReturn(saved);
                            });
                });
    }

    /**
     * Accepts a pending inter-agency cooperation request.
     *
     * @param command the accept response command
     * @return the accepted cooperation record
     */
    @Override
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<IncidentInterAgencyCooperation> accept(RespondToCooperationCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> {
                    IncidentInterAgencyCooperation accepted = coop.accept(command.getResponseDetails());
                    return cooperationRepository.save(accepted)
                            .flatMap(saved -> {
                                return incidentRepository.findById(saved.getIncidentId())
                                        .flatMap(incident -> {
                                            Incident inProgress = IncidentAgencyManagementService
                                                    .transition(incident::startInterAgencyCooperation);
                                            return incidentRepository.save(inProgress).thenReturn(saved);
                                        });
                            })
                            .flatMap(saved -> notifyRequestingAgency(saved, "ACCEPTED", "Cooperation accepted", ""))
                            .flatMap(saved -> {
                                IncidentInterAgencyCooperation active = saved.startProgress().markDriversNotified();
                                return cooperationRepository.save(active);
                            });
                });
    }

    /**
     * Rejects an inter-agency cooperation request.
     *
     * @param command the reject response command
     * @return the rejected cooperation record
     */
    @Override
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<IncidentInterAgencyCooperation> reject(RespondToCooperationCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> {
                    IncidentInterAgencyCooperation rejected = coop.reject(command.getRejectionReason());
                    return cooperationRepository.save(rejected)
                            // I21: back to the requesting agency's own handling
                            .flatMap(saved -> incidentRepository.findById(saved.getIncidentId())
                                    .flatMap(incident -> incidentRepository.save(IncidentAgencyManagementService
                                            .transition(() -> incident.startAgencyHandling(
                                                    incident.managerActor(null), "COOPERATION_REJECTED"))))
                                    .thenReturn(saved))
                            .flatMap(saved -> notifyRequestingAgency(saved, "REJECTED", "Cooperation rejected: " + command.getRejectionReason(),
                                    String.valueOf(command.getRejectionReason())));
                });
    }

    /**
     * Records successful completion of an inter-agency cooperation and anchors it on-chain.
     *
     * @param command the completion command
     * @return the completed cooperation record with blockchain proof
     */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "manage")
    public Mono<IncidentInterAgencyCooperation> complete(RecordCooperationCompletionCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> blockchainAuditPort.writeIncidentEvent(
                        coop.getIncidentId(), "INTER-AGENCY-" + coop.getId(),
                        "INTER_AGENCY_COOPERATION_COMPLETED",
                        buildCoopPayload(coop))
                        .flatMap(txHash -> {
                            IncidentInterAgencyCooperation completed = coop.complete(txHash);
                            return cooperationRepository.save(completed)
                                    // I22: cooperation succeeded → RESOLVED, delivery resumed (D16), unfreeze
                                    .flatMap(saved -> incidentRepository.findById(saved.getIncidentId())
                                            .flatMap(incident -> incidentRepository.save(IncidentAgencyManagementService
                                                    .transition(() -> incident.resolve(
                                                            ResolutionMode.INTERAGENCY_COOPERATION,
                                                            IncidentDeliveryOutcome.RESUMED,
                                                            IncidentResponsibleParty.NONE,
                                                            "Inter-agency cooperation completed",
                                                            "COOPERATION_COMPLETED", incident.managerActor(null)))))
                                            .flatMap(resolved -> resolutionEffects.afterResolved(resolved, null, null))
                                            .thenReturn(saved));
                        }));
    }

    private Mono<Void> notifyRespondingAgency(IncidentInterAgencyCooperation coop) {
        return notificationPort.notifyAgency(
                coop.getRespondingAgencyId(),
                "Inter-agency assistance requested",
                "Type: " + coop.getCooperationType() + " | Details: " + coop.getRequestDetails(),
                "INTERAGENCY_COOP_REQUESTED", coop.getIncidentId(),
                Map.of("cooperationType", String.valueOf(coop.getCooperationType()),
                        "details", String.valueOf(coop.getRequestDetails())));
    }

    private Mono<IncidentInterAgencyCooperation> notifyRequestingAgency(IncidentInterAgencyCooperation coop,
                                                                          String outcome, String message,
                                                                          String reason) {
        return notificationPort.notifyAgency(
                coop.getRequestingAgencyId(), "Cooperation update", message,
                "INTERAGENCY_COOP_" + outcome, coop.getIncidentId(),
                Map.of("outcome", outcome, "reason", reason)
        ).thenReturn(coop);
    }



    private String buildCoopPayload(IncidentInterAgencyCooperation coop) {
        return String.format("{\"type\":\"%s\",\"reqAgency\":\"%s\",\"respAgency\":\"%s\"}",
                coop.getCooperationType(), coop.getRequestingAgencyId(), coop.getRespondingAgencyId());
    }
}
