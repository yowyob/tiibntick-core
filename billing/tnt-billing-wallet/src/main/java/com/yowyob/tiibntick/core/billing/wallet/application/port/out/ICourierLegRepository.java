package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** {@code billing.wallet_courier_legs} (QO-11 / D-153). */
public interface ICourierLegRepository {

    /** Idempotent per delivery event: a replayed event is not recorded twice. */
    Mono<Void> insertIfAbsent(CourierLeg leg);

    /** The courier changes of a delivery, in delivery-version order. */
    Flux<CourierLeg> byDelivery(UUID deliveryId);
}
