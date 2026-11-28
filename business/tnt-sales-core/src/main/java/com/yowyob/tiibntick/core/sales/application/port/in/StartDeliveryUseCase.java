package com.yowyob.tiibntick.core.sales.application.port.in;

import com.yowyob.tiibntick.core.sales.domain.model.TntSalesOrder;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Use case — Mark order as in-delivery (DISPATCHED → IN_DELIVERY).
 *
 * <p>Typically triggered by {@code DeliveryPickedUp} of {@code tnt.delivery.events}
 * emitted by tnt-delivery-core when the deliverer picks up the parcel.</p>
 *
 * @author MANFOUO Braun
 */
public interface StartDeliveryUseCase {
    Mono<TntSalesOrder> startDelivery(UUID tenantId, UUID orderId);
}
