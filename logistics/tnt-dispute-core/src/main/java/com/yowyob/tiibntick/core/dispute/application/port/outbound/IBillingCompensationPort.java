package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.model.CompensationDetails;
import reactor.core.publisher.Mono;

/**
 * Secondary port for triggering compensation payments via tnt-billing-wallet.
 *
 * @author MANFOUO Braun
 */
public interface IBillingCompensationPort {

    /**
     * Initiates a compensation payment from the platform's reserve wallet
     * to the beneficiary's wallet or Mobile Money account.
     *
     * @param disputeId   the dispute requiring compensation
     * @param tenantId    the tenant scope
     * @param details     the approved compensation details
     * @return the payment transaction reference
     */
    Mono<String> initiateCompensationPayment(String disputeId, String tenantId, CompensationDetails details);
}
