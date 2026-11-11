package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort.CoreDeliveryState;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;

/**
 * Copies the lifecycle facts owned by tnt-delivery-core onto the gofp {@link Delivery} row.
 *
 * <p>The row is a <em>projection</em> of those facts (status, pickup and delivery instants) kept so
 * gofp queries and the legacy columns keep working; it is only ever written from a core result or a
 * core event, never decided independently.
 */
public final class CoreDeliveryProjection {

    private CoreDeliveryProjection() {
    }

    /** @return {@code true} when the row actually changed. */
    public static boolean apply(Delivery delivery, CoreDeliveryState state) {
        boolean changed = false;
        DeliveryStatus status = parse(state.status());
        if (status != null && status != delivery.getStatus()) {
            delivery.setStatus(status);
            changed = true;
        }
        if (state.actualPickupTime() != null && !state.actualPickupTime().equals(delivery.getActualPickupTime())) {
            delivery.setActualPickupTime(state.actualPickupTime());
            changed = true;
        }
        if (state.actualDeliveryTime() != null
                && !state.actualDeliveryTime().equals(delivery.getActualDeliveryTime())) {
            delivery.setActualDeliveryTime(state.actualDeliveryTime());
            changed = true;
        }
        return changed;
    }

    private static DeliveryStatus parse(String value) {
        try {
            return value == null ? null : DeliveryStatus.fromValue(value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
