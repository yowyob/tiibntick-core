package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.application.command.AttachEvidenceCommand;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEvidence;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentEventLog;
import com.yowyob.tiibntick.core.incident.port.inbound.IAttachEvidenceUseCase;
import com.yowyob.tiibntick.core.incident.port.outbound.IBlockchainAuditPort;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEvidenceRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentEventLogRepository;
import com.yowyob.tiibntick.core.incident.port.outbound.IIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Application service attaching and blockchain-anchoring digital evidence to incidents.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Service
@RequiredArgsConstructor
public class IncidentEvidenceService implements IAttachEvidenceUseCase {

    private final IIncidentEvidenceRepository evidenceRepository;
    private final IIncidentRepository incidentRepository;
    private final IIncidentEventLogRepository eventLogRepository;
    private final IBlockchainAuditPort blockchainAuditPort;

    @Override
    public Mono<IncidentEvidence> execute(AttachEvidenceCommand command) {
        return incidentRepository.findById(command.getIncidentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found")))
                .flatMap(incident -> {
                    IncidentEvidence evidence = IncidentEvidence.attach(
                            command.getIncidentId(), command.getEvidenceType(),
                            command.getFileUrl(), command.getMimeType(),
                            command.getCapturedByActorId(), command.getCapturedByRole(),
                            command.getSha256Checksum(), command.getLatitude(), command.getLongitude());

                    return evidenceRepository.save(evidence)
                            .flatMap(saved -> blockchainAuditPort.writeIncidentEvent(
                                    incident.getId(),
                                    incident.getOwnBlockchainChainId() != null
                                            ? incident.getOwnBlockchainChainId()
                                            : "MISSION-" + incident.getMissionId(),
                                    "EVIDENCE_ATTACHED",
                                    "{\"type\":\"" + saved.getEvidenceType() + "\",\"evidenceId\":\"" + saved.getId() + "\"}"
                            ).flatMap(txHash -> {
                                IncidentEvidence withProof = saved.validate(command.getCapturedByActorId(), txHash);
                                return evidenceRepository.save(withProof);
                            }))
                            .flatMap(saved -> {
                                IncidentEventLog log = IncidentEventLog.of(
                                        command.getIncidentId(), "EVIDENCE_ATTACHED",
                                        command.getCapturedByActorId(), command.getCapturedByRole(),
                                        "{\"evidenceId\":\"" + saved.getId() + "\",\"type\":\"" + saved.getEvidenceType() + "\"}");
                                return eventLogRepository.save(log).thenReturn(saved);
                            });
                });
    }
}
