package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/** Cash collected by a courier for the Kernel (spec §4.1 mode CASH, §4.2 K1–K5, lot L5). */
public interface ICashCollectionUseCase {

    /**
     * K1: opens the collection of a CASH intent (platform identity), confirmed by the payer; idempotent per delivery.
     * 403 when the caller is not the payer of the intent.
     */
    Mono<CashCollectionMirror> open(UUID intentId, UUID callerActorId);

    /** K2: the courier declared the amount collected (delivery validated). Empty without a collection. */
    Mono<CashCollectionMirror> collected(UUID deliveryId, BigDecimal amount);

    /** K5: cancelled while still OPEN. Empty without a collection; unchanged once collected. */
    Mono<CashCollectionMirror> cancel(UUID deliveryId);

    /** K4 (courier's bearer): the code to remit the platform's part. 403 when the caller is not the courier. */
    Mono<Challenge> remitChallenge(UUID deliveryId, UUID courierActorId);

    /** K4 (courier's bearer). */
    Mono<CashCollectionMirror> remit(UUID deliveryId, UUID courierActorId, String challengeToken, String code);

    /** The courier's collections not yet remitted (his cash debt). */
    Flux<CashCollectionMirror> outstanding(UUID courierActorId);
}
