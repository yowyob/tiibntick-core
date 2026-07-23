package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.event.*;
import reactor.core.publisher.Mono;

/**
 * Secondary port — event publishing contract (implemented by KafkaPublisher).
 * All billing wallet domain events are published via this interface.
 *
 * @author MANFOUO Braun
 */
public interface IWalletEventPublisher {
    Mono<Void> publish(PaymentInitiated event);
    Mono<Void> publish(PaymentConfirmed event);
    Mono<Void> publish(PaymentFailed event);
    Mono<Void> publish(WalletCredited event);
    Mono<Void> publish(WalletDebited event);
    Mono<Void> publish(CommissionCalculated event);
    Mono<Void> publish(WalletSplitExecuted event);
}
