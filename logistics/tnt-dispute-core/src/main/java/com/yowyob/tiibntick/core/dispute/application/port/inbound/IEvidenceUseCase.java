package com.yowyob.tiibntick.core.dispute.application.port.inbound;

import com.yowyob.tiibntick.core.dispute.application.command.AddEvidenceCommand;
import com.yowyob.tiibntick.core.dispute.application.command.RequestEvidenceCommand;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeEvidence;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port (inbound) for evidence management operations.
 *
 * @author MANFOUO Braun
 */
public interface IEvidenceUseCase {

    /**
     * Submits evidence to a dispute. Optionally anchors the evidence on the blockchain
     * via tnt-trust if the evidence type supports it.
     *
     * @param cmd the add evidence command
     * @return the updated dispute with the evidence added
     */
    Mono<Dispute> submitEvidence(AddEvidenceCommand cmd);

    /**
     * Requests additional evidence from a party in a dispute.
     *
     * @param cmd the request evidence command
     * @return the updated dispute in AWAITING_EVIDENCE state
     */
    Mono<Dispute> requestEvidence(RequestEvidenceCommand cmd);

    /**
     * Verifies a specific piece of evidence (mediator action).
     * May trigger blockchain anchoring if not already done.
     *
     * @param disputeId  the dispute ID
     * @param evidenceId the evidence ID to verify
     * @param mediatorId the mediator performing the verification
     * @param tenantId   the tenant scope
     * @return the verified evidence entity
     */
    Mono<DisputeEvidence> verifyEvidence(DisputeId disputeId, String evidenceId, String mediatorId, String tenantId);

    /**
     * Returns all evidence for a given dispute.
     *
     * @param disputeId the dispute ID
     * @param tenantId  the tenant scope
     * @return a Flux of evidence records
     */
    Flux<DisputeEvidence> getEvidenceForDispute(DisputeId disputeId, String tenantId);
}
