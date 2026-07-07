package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.ConfirmHandoverCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.ResolutionMode;
import com.yowyob.tiibntick.core.incident.domain.event.IncidentDomainEvents.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentDriverReplacement;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IConfirmHandoverUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service managing the double-confirmation driver handover protocol with blockchain anchoring.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentHandoverService implements IConfirmHandoverUseCase {

    private final IIncidentRepository incidentRepository;
    private final IIncidentDriverReplacementRepository replacementRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IIncidentBlockchainRepository blockchainRepository;
    private final IIncidentEventPublisher eventPublisher;
    private final INotificationPort notificationPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IMissionStatusPort missionStatusPort;
    private final IPaymentFreezePort paymentFreezePort;

    /**
     * Processes one side of the dual-confirmation handover.
     * When both parties confirm, the handover is blockchain-anchored and the mission is resumed.
     *
     * @param command the confirmation command
     * @return the updated driver replacement record
     */
    @Override
    public Mono<IncidentDriverReplacement> execute(ConfirmHandoverCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> replacementRepository.findReplacementByIncidentId(incident.getId())
                        .flatMap(rep -> processConfirmation(incident, rep, command)));
    }

    private Mono<IncidentDriverReplacement> processConfirmation(Incident incident,
                                                                  IncidentDriverReplacement rep,
                                                                  ConfirmHandoverCommand command) {
        IncidentDriverReplacement updated = command.isConfirmingAsOriginalDriver()
                ? rep.confirmByOriginalDriver()
                : rep.confirmByReplacementDriver();

        return replacementRepository.save(updated)
                .flatMap(saved -> {
                    if (saved.isHandoverComplete()) {
                        return finalizeHandover(incident, saved);
                    }
                    return logPartialConfirmation(incident, saved, command.getActorId()).thenReturn(saved);
                });
    }

    private Mono<IncidentDriverReplacement> finalizeHandover(Incident incident,
                                                               IncidentDriverReplacement rep) {
        return blockchainAuditPort.writeIncidentEvent(
                incident.getId(),
                incident.getOwnBlockchainChainId() != null
                        ? incident.getOwnBlockchainChainId()
                        : "MISSION-" + incident.getMissionId(),
                "PARCEL_HANDOVER_COMPLETED",
                buildHandoverPayload(rep)
        ).flatMap(txHash -> {
            IncidentDriverReplacement withProof = rep.withBlockchainProof(txHash);
            return replacementRepository.save(withProof)
                    .flatMap(saved -> {
                        IncidentEventLog log = IncidentEventLog.of(
                                incident.getId(), "HANDOVER_COMPLETED",
                                rep.getReplacementDriverId(), ActorRole.SYSTEM,
                                "{\"txHash\":\"" + txHash + "\"}")
                                .withBlockchainProof(txHash, incident.getOwnBlockchainChainId(), true, true);
                        return eventLogRepository.save(log)
                                .then(resumeMissionAndResolve(incident, rep, txHash))
                                .thenReturn(saved);
                    });
        });
    }

    private Mono<Void> resumeMissionAndResolve(Incident incident, IncidentDriverReplacement rep,
                                                String txHash) {
        Incident resolved = incident.resolve(ResolutionMode.FULLY_AUTOMATIC);
        var handoverEvent = HandoverCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .incidentId(incident.getId())
                .tenantId(incident.getTenantId())
                .missionId(incident.getMissionId())
                .originalDriverId(rep.getOriginalDriverId())
                .replacementDriverId(rep.getReplacementDriverId())
                .blockchainTxHash(txHash)
                .occurredAt(Instant.now())
                .build();

        return incidentRepository.save(resolved)
                .then(missionStatusPort.resumeMission(incident.getMissionId(),
                        rep.getReplacementDriverId(), rep.getReplacementVehicleId()))
                .then(paymentFreezePort.unfreezePayment(incident.getMissionId(), "Handover completed"))
                .then(eventPublisher.publish(handoverEvent))
                .then(notificationPort.notifyActor(rep.getOriginalDriverId(),
                        "Handover confirmed", "Parcel transfer recorded on blockchain",
                        "HANDOVER_CONFIRMED", incident.getId()))
                .then(notificationPort.notifyActor(rep.getReplacementDriverId(),
                        "Mission started", "You are now the assigned driver",
                        "DRIVER_MISSION_ASSIGNED", incident.getId()));
    }

    private Mono<Void> logPartialConfirmation(Incident incident, IncidentDriverReplacement rep,
                                               UUID actorId) {
        IncidentEventLog log = IncidentEventLog.of(
                incident.getId(), "HANDOVER_PARTIALLY_CONFIRMED",
                actorId, ActorRole.SYSTEM,
                "{\"status\":\"" + rep.getHandoverStatus() + "\"}");
        return eventLogRepository.save(log).then();
    }

    private String buildHandoverPayload(IncidentDriverReplacement rep) {
        return String.format(
                "{\"originalDriver\":\"%s\",\"replacementDriver\":\"%s\",\"lat\":%.6f,\"lng\":%.6f}",
                rep.getOriginalDriverId(), rep.getReplacementDriverId(),
                rep.getHandoverLatitude(), rep.getHandoverLongitude());
    }
}
