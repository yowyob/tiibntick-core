package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import reactor.core.publisher.Mono;

/** {@code tnt.billing.compensation.paid} (spec §4.3), through the outbox. */
public interface ICompensationEventPublisher {

    Mono<Void> compensationPaid(WalletCompensation compensation);
}
