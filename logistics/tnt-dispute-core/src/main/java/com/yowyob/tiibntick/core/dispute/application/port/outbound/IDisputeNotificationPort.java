package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import reactor.core.publisher.Mono;

/**
 * Secondary port for sending dispute-related notifications via tnt-notify-core.
 *
 * @author MANFOUO Braun
 */
public interface IDisputeNotificationPort {

    /**
     * Notifies all involved parties that a dispute has been opened.
     *
     * @param dispute the newly opened dispute
     * @return a Mono that completes when notifications are dispatched
     */
    Mono<Void> notifyDisputeOpened(Dispute dispute);

    /**
     * Notifies the mediator of their assignment and both parties of the investigator.
     *
     * @param dispute the dispute with the assigned mediator
     * @return a Mono that completes when notifications are dispatched
     */
    Mono<Void> notifyMediatorAssigned(Dispute dispute);

    /**
     * Notifies both parties that a ruling has been issued.
     *
     * @param dispute the dispute with the ruling attached
     * @return a Mono that completes when notifications are dispatched
     */
    Mono<Void> notifyRulingIssued(Dispute dispute);

    /**
     * Notifies the beneficiary that their compensation payment has been processed.
     *
     * @param dispute the dispute with the paid compensation
     * @return a Mono that completes when notifications are dispatched
     */
    Mono<Void> notifyCompensationPaid(Dispute dispute);

    /**
     * Notifies all parties that the dispute has been formally closed.
     *
     * @param dispute the closed dispute
     * @return a Mono that completes when notifications are dispatched
     */
    Mono<Void> notifyDisputeClosed(Dispute dispute);

    /**
     * Sends an SLA breach alert to the dispute management team.
     *
     * @param dispute    the dispute with the breached SLA
     * @param breachType a description of which SLA was breached
     * @return a Mono that completes when the alert is dispatched
     */
    Mono<Void> notifySlaBreached(Dispute dispute, String breachType);
}
