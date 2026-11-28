package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Payment intents and mirrors of the Kernel escrows and cash collections (lot L5). */
public interface IPaymentMirrorRepository {

    Mono<PaymentIntent> findIntent(UUID intentId);

    Mono<PaymentIntent> findIntentByDelivery(UUID deliveryId);

    Mono<PaymentIntent> saveIntent(PaymentIntent intent, boolean isNew);

    Mono<EscrowMirror> findEscrow(UUID deliveryId, String purpose);

    Mono<EscrowMirror> saveEscrow(EscrowMirror escrow, boolean isNew);

    Mono<CashCollectionMirror> findCashCollection(UUID deliveryId);

    Mono<CashCollectionMirror> saveCashCollection(CashCollectionMirror collection, boolean isNew);

    /** Collections of a courier not yet remitted (OPEN or COLLECTED), oldest first. */
    Flux<CashCollectionMirror> outstandingCashCollections(UUID courierActorId);
}
