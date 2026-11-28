package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;

/**
 * Puts what {@code tnt-delivery-core} says about a delivery (status, assigned delivery person, real
 * instants, agreed price and windows) onto the gofp {@link Delivery} extension row, for reading.
 *
 * <p>Nothing here is persisted: the facts have no column mapped on the gofp row any more. The core is the
 * only place they are written.
 */
public final class CoreDeliveryAssembler {

    private CoreDeliveryAssembler() {
    }

    /** @return {@code delivery}, now carrying the core's facts (unchanged when {@code state} is null) */
    public static Delivery hydrate(Delivery delivery, CoreDeliveryState state) {
        if (delivery == null || state == null) {
            return delivery;
        }
        return delivery.readThrough(state.statusValue(), state.deliveryPersonId(), state.agreedAmount(),
                state.pickupWindowStart(), state.pickupWindowEnd(),
                state.deliveryWindowStart(), state.deliveryWindowEnd(),
                state.actualPickupTime(), state.actualDeliveryTime());
    }
}
