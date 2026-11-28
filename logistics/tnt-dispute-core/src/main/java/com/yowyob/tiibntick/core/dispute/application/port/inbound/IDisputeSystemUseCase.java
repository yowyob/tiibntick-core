package com.yowyob.tiibntick.core.dispute.application.port.inbound;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.command.ProcessCompensationCommand;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;
import reactor.core.publisher.Mono;

/**
 * Dispute changes made by the platform itself, without a user session (Kafka consumers, schedulers; lot L4). These
 * entry points carry no {@code @RequirePermission}: the guarded user-facing ones failed for lack of a security context
 * and the failure was swallowed (C15).
 */
public interface IDisputeSystemUseCase {

    /** LT1 automatic opening on {@code IncidentEscalatedToDispute} (claimant SYSTEM). */
    Mono<Dispute> openFromIncident(OpenDisputeCommand cmd);

    /** LT9 confirmed by billing ({@code tnt.billing.compensation.paid}). */
    Mono<Dispute> confirmCompensationPaid(ProcessCompensationCommand cmd);

    /** LT11: the deadline passed; nothing when the dispute is no longer in OPEN, UNDER_INVESTIGATION or AWAITING_EVIDENCE. */
    Mono<Dispute> expire(DisputeId disputeId, String tenantId);
}
