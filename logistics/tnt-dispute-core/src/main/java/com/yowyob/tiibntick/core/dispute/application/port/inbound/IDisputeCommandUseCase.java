package com.yowyob.tiibntick.core.dispute.application.port.inbound;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import reactor.core.publisher.Mono;

/**
 * Primary port (inbound) defining all write operations on disputes.
 * Implemented by {@code DisputeCommandService} in the application layer.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeCommandUseCase {

    /**
     * Opens a new dispute. Validates uniqueness (no active dispute for the same package),
     * creates the aggregate, and emits the {@code DisputeOpened} event.
     *
     * @param cmd the opening command
     * @return the created dispute
     */
    Mono<Dispute> openDispute(OpenDisputeCommand cmd);

    /**
     * Assigns a mediator to an open dispute and starts the investigation phase.
     *
     * @param cmd the assignment command
     * @return the updated dispute
     */
    Mono<Dispute> assignMediator(AssignMediatorCommand cmd);

    /**
     * Starts the formal mediation phase.
     *
     * @param cmd the start mediation command
     * @return the updated dispute
     */
    Mono<Dispute> startMediation(StartMediationCommand cmd);

    /**
     * Issues a ruling on a dispute under mediation or arbitration.
     *
     * @param cmd the ruling command
     * @return the updated dispute
     */
    Mono<Dispute> ruleDispute(RuleDisputeCommand cmd);

    /**
     * Escalates a dispute to arbitration.
     *
     * @param cmd the escalation command
     * @return the updated dispute
     */
    Mono<Dispute> escalateDispute(EscalateDisputeCommand cmd);

    /**
     * Marks the compensation as paid after receiving confirmation from tnt-billing-wallet.
     *
     * @param cmd the process compensation command
     * @return the updated dispute
     */
    Mono<Dispute> processCompensation(ProcessCompensationCommand cmd);

    /**
     * Closes a dispute (admin or system closure).
     *
     * @param cmd the close command
     * @return the updated dispute
     */
    Mono<Dispute> closeDispute(CloseDisputeCommand cmd);

    /**
     * Withdraws a dispute at the claimant's request.
     *
     * @param cmd the withdraw command
     * @return the updated dispute
     */
    Mono<Dispute> withdrawDispute(WithdrawDisputeCommand cmd);

    /**
     * Posts a comment on the dispute thread.
     *
     * @param cmd the add comment command
     * @return the updated dispute
     */
    Mono<Dispute> addComment(AddCommentCommand cmd);

    /**
     * Opens a dispute specifically targeting a FreelancerOrganization ().
     *
     * <p>Pre-sets respondentType = FREELANCER_ORG, respondentOrgId = org UUID,
     * and optionally impliedSubDelivererId when a SUB_DELIVERER executed the delivery.
     *
     * @param cmd the FreelancerOrg-specific opening command
     * @return the created dispute
     */
    Mono<Dispute> openAgainstFreelancerOrg(OpenDisputeAgainstFreelancerOrgCommand cmd);
}
