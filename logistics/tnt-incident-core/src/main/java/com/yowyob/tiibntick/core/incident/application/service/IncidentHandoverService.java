package com.yowyob.tiibntick.core.incident.application.service;

import java.util.Map;
import com.yowyob.tiibntick.core.incident.application.command.ConfirmHandoverCommand;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentDriverReplacement;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IConfirmHandoverUseCase;
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
    private final INotificationPort notificationPort;
    private final IBlockchainAuditPort blockchainAuditPort;
    private final IDeliveryStatusPort deliveryStatusPort;
    private final IPaymentFreezePort paymentFreezePort;
    private final IncidentResolutionEffects resolutionEffects;
    private final com.yowyob.tiibntick.core.incident.config.IncidentLifecycleProperties lifecycle;

    /**
     * Processes one side of the dual-confirmation handover.
     * When both parties confirm, the handover is blockchain-anchored and the mission is resumed.
     *
     * @param command the confirmation command
     * @return the updated driver replacement record
     */
    @Override
    @Transactional
    @RequirePermission(resource = "incident", action = "create")
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

        if (incident.getStatus() != IncidentStatus.AWAITING_HANDOVER) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                    "Incident " + incident.getId() + " is not waiting for a hand-over (" + incident.getStatus() + ")"));
        }
        UUID expected = command.isConfirmingAsOriginalDriver() ? rep.getOriginalDriverId() : rep.getReplacementDriverId();
        if (command.getActorId() == null || !command.getActorId().equals(expected)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the couriers of the hand-over may confirm it"));
        }
        String side = command.isConfirmingAsOriginalDriver() ? "ORIGINAL" : "REPLACEMENT";
        com.yowyob.tiibntick.common.kafka.TntEventActor actor = com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                com.yowyob.tiibntick.common.kafka.TntEventActor.Type.COURIER, command.getActorId());
        return replacementRepository.save(updated)
                .flatMap(saved -> incidentRepository.save(incident.recordHandoverConfirmed(side, actor))
                        .flatMap(withConfirmation -> saved.isHandoverComplete()
                                ? finalizeHandover(withConfirmation, saved)
                                : logPartialConfirmation(withConfirmation, saved, command.getActorId())
                                .thenReturn(saved)));
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
        Incident resolved = incident.resolve(ResolutionMode.FULLY_AUTOMATIC, IncidentDeliveryOutcome.RESUMED,
                IncidentResponsibleParty.NONE, "Hand-over completed", "HANDOVER_COMPLETED",
                com.yowyob.tiibntick.common.kafka.TntEventActor.system());
        // I14 then D18 (resume with the replacement courier) and unfreeze
        return incidentRepository.save(resolved)
                .flatMap(saved -> resolutionEffects.afterResolved(saved, rep.getReplacementDriverId(),
                        rep.getReplacementVehicleId(), rep.getHandoverLatitude(), rep.getHandoverLongitude()))
                .then(notificationPort.notifyActor(rep.getOriginalDriverId(),
                        "Handover confirmed", "Parcel transfer recorded on blockchain",
                        "HANDOVER_CONFIRMED", incident.getId(),
                        Map.of("reference", String.valueOf(incident.getReferenceCode()))))
                .then(notificationPort.notifyActor(rep.getReplacementDriverId(),
                        "Mission started", "You are now the assigned driver",
                        "DRIVER_MISSION_ASSIGNED", incident.getId(),
                        Map.of("reference", String.valueOf(incident.getReferenceCode()))));
    }

    private Mono<Void> logPartialConfirmation(Incident incident, IncidentDriverReplacement rep,
                                               UUID actorId) {
        IncidentEventLog log = IncidentEventLog.of(
                incident.getId(), "HANDOVER_PARTIALLY_CONFIRMED",
                actorId, ActorRole.SYSTEM,
                "{\"status\":\"" + rep.getHandoverStatus() + "\"}");
        return eventLogRepository.save(log).then();
    }

    /**
     * I15 then D19: a hand-over not confirmed by both couriers in time is aborted; the incident searches a
     * replacement again (REASSIGNING_DRIVER) and the delivery goes back to PAUSED_BY_INCIDENT.
     */
    public Mono<Long> abortExpiredHandovers(Instant now) {
        Instant limit = now.minus(lifecycle.getHandoverTimeout());
        return incidentRepository.findAwaitingHandover(lifecycle.getBatchSize())
                .concatMap(incident -> replacementRepository.findReplacementByIncidentId(incident.getId())
                        .filter(rep -> rep.getHandoverScheduledAt() != null && rep.getHandoverScheduledAt().isBefore(limit))
                        .flatMap(rep -> abortHandover(incident, "hand-over not confirmed within "
                                + lifecycle.getHandoverTimeout())))
                .count();
    }

    public Mono<Incident> abortHandover(Incident incident, String reason) {
        return replacementRepository.findReplacementByIncidentId(incident.getId())
                .flatMap(rep -> replacementRepository.save(rep.markTimedOut()))
                .then(incidentRepository.save(incident.startReassigningDriver()))
                .flatMap(saved -> deliveryStatusPort.abortHandover(saved.getMissionId()).thenReturn(saved))
                .flatMap(saved -> eventLogRepository.save(IncidentEventLog.of(saved.getId(), "HANDOVER_ABORTED",
                        null, ActorRole.SYSTEM, "{\"reason\":\"" + reason + "\"}")).thenReturn(saved));
    }

    private String buildHandoverPayload(IncidentDriverReplacement rep) {
        return String.format(
                "{\"originalDriver\":\"%s\",\"replacementDriver\":\"%s\",\"lat\":%.6f,\"lng\":%.6f}",
                rep.getOriginalDriverId(), rep.getReplacementDriverId(),
                rep.getHandoverLatitude(), rep.getHandoverLongitude());
    }
}
