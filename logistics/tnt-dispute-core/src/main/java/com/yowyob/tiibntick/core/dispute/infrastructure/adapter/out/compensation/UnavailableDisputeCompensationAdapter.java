package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.compensation;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeCompensationPort;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Default {@link IDisputeCompensationPort} until billing-wallet provides the Kernel one (lot L5): every payment is
 * refused with {@code 503 COMPENSATION_UNAVAILABLE}, the dispute stays PENDING_COMPENSATION.
 */
public class UnavailableDisputeCompensationAdapter implements IDisputeCompensationPort {

    @Override
    public Mono<String> pay(String tenantId, String disputeId, String beneficiaryActorId, BigDecimal amount,
                            String currency) {
        return Mono.error(DisputeCompensationException.unavailable());
    }
}
