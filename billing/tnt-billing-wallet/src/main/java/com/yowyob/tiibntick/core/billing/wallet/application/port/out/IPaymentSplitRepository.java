package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentSplit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for PaymentSplit persistence.
 *
 * @author MANFOUO Braun
 */
public interface IPaymentSplitRepository {
    Mono<PaymentSplit> save(PaymentSplit split);
    Mono<PaymentSplit> findById(UUID id);
    Flux<PaymentSplit> findByMissionId(String missionId);
    Flux<PaymentSplit> findByFreelancerOrgId(String freelancerOrgId);
}
