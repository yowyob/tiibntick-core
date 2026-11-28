package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import reactor.core.publisher.Mono;

/**
 * Outbound port — the wallet events the Core now publishes itself after a settlement was
 * recorded (risk R1 of the Kafka audit, decided 2026-11-26: the Kernel wallet emits nothing the
 * Core's consumers can read, so the Core's settlement service is the producer).
 *
 * <p>Implementations go through the yow-event-kernel outbox (persisted envelope, automatic relay),
 * so a broker outage delays the event instead of losing it.
 */
public interface ISettlementEventPublisher {

    /** {@code tnt.billing.wallet.payment.confirmed} — the settlement just became PAID. */
    Mono<Void> paymentConfirmed(WalletSettlement settlement);

    /** {@code tnt.billing.wallet.commission.calculated} — a platform commission just became DUE. */
    Mono<Void> commissionCalculated(WalletSettlement settlement);
}
