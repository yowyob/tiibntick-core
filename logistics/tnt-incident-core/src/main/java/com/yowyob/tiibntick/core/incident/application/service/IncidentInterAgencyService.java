package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.*;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.port.inbound.IInterAgencyCooperationUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    private final IBlockchainAuditPort blockchainAuditPort;

    /**
     * Creates a new inter-agency cooperation request and notifies the responding agency.
     *
     * @param command the cooperation request command
     * @return the created cooperation record
     */
    @Override
    public Mono<IncidentInterAgencyCooperation> request(RequestCooperationCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    IncidentInterAgencyCooperation coop = IncidentInterAgencyCooperation.request(
                            command.getIncidentId(), command.getRequestingAgencyId(),
                            command.getRespondingAgencyId(), command.getCooperationType(),
                            command.getDetails());
                    return cooperationRepository.save(coop)
                            .flatMap(saved -> {
                                Incident waiting = incident.waitForInterAgency();
                                return incidentRepository.save(waiting)
                                        .then(notifyRespondingAgency(saved))
                                        .then(publishCoopRequested(saved))
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
    public Mono<IncidentInterAgencyCooperation> accept(RespondToCooperationCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> {
                    IncidentInterAgencyCooperation accepted = coop.accept(command.getResponseDetails());
                    return cooperationRepository.save(accepted)
                            .flatMap(saved -> {
                                return incidentRepository.findById(saved.getIncidentId())
                                        .flatMap(incident -> {
                                            Incident inProgress = incident.startInterAgencyCooperation();
                                            return incidentRepository.save(inProgress).thenReturn(saved);
                                        });
                            })
                            .flatMap(saved -> notifyRequestingAgency(saved, "Cooperation accepted"))
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
    public Mono<IncidentInterAgencyCooperation> reject(RespondToCooperationCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> {
                    IncidentInterAgencyCooperation rejected = coop.reject(command.getRejectionReason());
                    return cooperationRepository.save(rejected)
                            .flatMap(saved -> notifyRequestingAgency(saved, "Cooperation rejected: " + command.getRejectionReason()));
                });
    }

    /**
     * Records successful completion of an inter-agency cooperation and anchors it on-chain.
     *
     * @param command the completion command
     * @return the completed cooperation record with blockchain proof
     */
    @Override
    public Mono<IncidentInterAgencyCooperation> complete(RecordCooperationCompletionCommand command) {
        return cooperationRepository.findCooperationById(command.getCooperationId())
                .flatMap(coop -> blockchainAuditPort.writeIncidentEvent(
                        coop.getIncidentId(), "INTER-AGENCY-" + coop.getId(),
                        "INTER_AGENCY_COOPERATION_COMPLETED",
                        buildCoopPayload(coop))
                        .flatMap(txHash -> {
                            IncidentInterAgencyCooperation completed = coop.complete(txHash);
                            return cooperationRepository.save(completed)
                                    .flatMap(saved -> publishCoopCompleted(saved));
                        }));
    }

    private Mono<Void> notifyRespondingAgency(IncidentInterAgencyCooperation coop) {
        return notificationPort.notifyAgency(
                coop.getRespondingAgencyId(),
                "Inter-agency assistance requested",
                "Type: " + coop.getCooperationType() + " | Details: " + coop.getRequestDetails(),
                "INTERAGENCY_COOP_REQUESTED", coop.getIncidentId());
    }

    private Mono<IncidentInterAgencyCooperation> notifyRequestingAgency(IncidentInterAgencyCooperation coop,
                                                                          String message) {
        return notificationPort.notifyAgency(
                coop.getRequestingAgencyId(), "Cooperation update", message,
                "INTERAGENCY_COOP_UPDATED", coop.getIncidentId()
        ).thenReturn(coop);
    }

    private Mono<Void> publishCoopRequested(IncidentInterAgencyCooperation coop) {
        var event = InterAgencyCoopRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(coop.getIncidentId())
                .tenantId(null)
                .requestingAgencyId(coop.getRequestingAgencyId())
                .respondingAgencyId(coop.getRespondingAgencyId())
                .cooperationType(coop.getCooperationType())
                .details(coop.getRequestDetails())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event);
    }

    private Mono<IncidentInterAgencyCooperation> publishCoopCompleted(IncidentInterAgencyCooperation coop) {
        var event = InterAgencyCoopCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(coop.getIncidentId())
                .tenantId(null)
                .requestingAgencyId(coop.getRequestingAgencyId())
                .respondingAgencyId(coop.getRespondingAgencyId())
                .blockchainTxHash(coop.getBlockchainTxHash())
                .occurredAt(Instant.now())
                .build();
        return eventPublisher.publish(event).thenReturn(coop);
    }

    private String buildCoopPayload(IncidentInterAgencyCooperation coop) {
        return String.format("{\"type\":\"%s\",\"reqAgency\":\"%s\",\"respAgency\":\"%s\"}",
                coop.getCooperationType(), coop.getRequestingAgencyId(), coop.getRespondingAgencyId());
    }
}
