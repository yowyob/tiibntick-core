package com.yowyob.tiibntick.core.dispute.application.port.inbound;

import com.yowyob.tiibntick.core.dispute.application.command.EscalateDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.command.RuleDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.command.StartMediationCommand;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import reactor.core.publisher.Mono;

/**
 * Primary port (inbound) for mediation and arbitration operations.
 *
 * @author MANFOUO Braun
 */
public interface IMediationUseCase {

    /**
     * Starts the formal mediation phase once sufficient evidence is collected.
     *
     * @param cmd the start mediation command
     * @return the updated dispute in MEDIATION_IN_PROGRESS state
     */
    Mono<Dispute> startMediation(StartMediationCommand cmd);

    /**
     * Issues a formal ruling on the dispute (by mediator or arbitrator).
     *
     * @param cmd the rule command
     * @return the updated dispute
     */
    Mono<Dispute> ruleDispute(RuleDisputeCommand cmd);

    /**
     * Escalates the dispute when mediation fails or fraud is suspected.
     *
     * @param cmd the escalation command
     * @return the updated dispute in PENDING_ARBITRATION state
     */
    Mono<Dispute> escalateDispute(EscalateDisputeCommand cmd);
}
