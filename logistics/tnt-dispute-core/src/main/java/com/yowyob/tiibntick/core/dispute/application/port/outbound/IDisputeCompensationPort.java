package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Pays a dispute compensation from the Kernel compensation fund (spec §4.3). Declared here, implemented by
 * billing-wallet (lot L5), which depends down on this module; until then the default adapter answers
 * {@code 503 COMPENSATION_UNAVAILABLE}.
 */
public interface IDisputeCompensationPort {

    /**
     * Idempotent per dispute: a second call for the same dispute returns the first payment.
     *
     * @return the Kernel payment reference
     * @throws DisputeCompensationException {@code COMPENSATION_UNAVAILABLE} (503) or
     *                                      {@code COMPENSATION_FUND_INSUFFICIENT} (409), as an error signal
     */
    Mono<String> pay(String tenantId, String disputeId, String beneficiaryActorId, BigDecimal amount,
                     String currency);
}
