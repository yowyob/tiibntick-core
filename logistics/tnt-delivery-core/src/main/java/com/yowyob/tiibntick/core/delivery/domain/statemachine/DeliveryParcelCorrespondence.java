package com.yowyob.tiibntick.core.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;

import java.util.Objects;
import java.util.Optional;

import static com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine.*;
import static com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine.*;

/**
 * Delivery → parcel correspondence (spec §2.1, decision [C bis]): for every delivery row and every delivery kind, the
 * parcel row applied <b>in the same operation</b>, or none ("=" in the spec). Coded once, here; the tests check that
 * every effect is a pair of {@link ParcelStateMachine#TABLE} for every parcel state the delivery can meet (no hole),
 * and the inverse table §2.2 is checked against it.
 */
public final class DeliveryParcelCorrespondence {

    /**
     * A parcel transition caused by a delivery transition.
     *
     * @param parcelRow   row of {@link ParcelStateMachine#TABLE}
     * @param target      parcel state reached
     * @param returnCause why the parcel goes back (only for {@code RETURN_REQUESTED}), else {@code null}
     * @param trigger     envelope trigger of the parcel event
     */
    public record ParcelEffect(String parcelRow, ParcelStatus target, ParcelReturnCause returnCause, String trigger) {
        public ParcelEffect {
            Objects.requireNonNull(parcelRow, "parcelRow");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(trigger, "trigger");
        }
    }

    /**
     * Context of a delivery transition needed to choose the parcel effect.
     *
     * @param kind              nature of the delivery
     * @param deliveryRow       delivery row applied
     * @param operationalState  the state the delivery was in, or — when it was in an incident state — the memorised
     *                          operational state (tells whether the parcel was collected)
     * @param parcelState       current parcel state (the memorised state when the parcel is under dispute)
     * @param failureReason     reason of a D10 failure, else {@code null}
     */
    public record Context(DeliveryKind kind, String deliveryRow, DeliveryStatus operationalState,
                          ParcelStatus parcelState, DeliveryFailureReason failureReason) {
    }

    private DeliveryParcelCorrespondence() {
    }

    /** @return whether the delivery had collected the parcel in this operational state */
    public static boolean collected(DeliveryStatus operationalState) {
        return operationalState != null
                && (operationalState.courierHoldsParcel() || operationalState == DeliveryStatus.AT_RELAY_HUB);
    }

    /** @return the parcel effect of the delivery transition, or empty when the parcel does not change */
    public static Optional<ParcelEffect> effectOf(Context ctx) {
        boolean ret = ctx.kind() == DeliveryKind.RETURN;
        return Optional.ofNullable(switch (ctx.deliveryRow()) {
            case D2 -> ret ? null : new ParcelEffect(P2, ParcelStatus.ASSIGNED, null, "DELIVERY_ASSIGNED");
            case D3 -> afterPickup(ctx)
                    ? (ret ? returnFailed(ParcelReturnCause.RETURN_DELIVERY_CANCELLED)
                           : new ParcelEffect(P11, ParcelStatus.RETURN_REQUESTED,
                                   ParcelReturnCause.SENDER_CANCELLED_AFTER_PICKUP, "DELIVERY_CANCELLED"))
                    : backToCreated(ctx, "DELIVERY_CANCELLED");
            case D4 -> ret ? new ParcelEffect(P18, ParcelStatus.RETURN_IN_TRANSIT, null, "DELIVERY_PICKED_UP")
                           : new ParcelEffect(P4, ParcelStatus.PICKED_UP, null, "DELIVERY_PICKED_UP");
            case D5 -> ret ? null : new ParcelEffect(P5, ParcelStatus.IN_TRANSIT, null, "DELIVERY_IN_TRANSIT");
            case D6 -> ret ? null : new ParcelEffect(P6, ParcelStatus.IN_TRANSIT, null, "DELIVERY_IN_TRANSIT");
            case D7 -> ret ? null : new ParcelEffect(P7, ParcelStatus.AT_RELAY_HUB, null, "DELIVERY_AT_RELAY_HUB");
            case D8 -> ret ? null
                           : new ParcelEffect(P8, ParcelStatus.OUT_FOR_DELIVERY, null, "DELIVERY_OUT_FOR_DELIVERY");
            case D9 -> ret ? null
                           : new ParcelEffect(P9, ParcelStatus.DELIVERY_FAILED, null, "DELIVERY_ATTEMPT_FAILED");
            case D10 -> ret ? returnFailed(ParcelReturnCause.RETURN_DELIVERY_FAILED)
                            : new ParcelEffect(P11, ParcelStatus.RETURN_REQUESTED,
                                    ctx.failureReason() == DeliveryFailureReason.CLIENT_REFUSED_RECEPTION
                                            ? ParcelReturnCause.CLIENT_REFUSED_RECEPTION
                                            : ParcelReturnCause.MAX_ATTEMPTS_REACHED,
                                    "DELIVERY_FAILED");
            case D11 -> ret ? new ParcelEffect(P20, ParcelStatus.RETURNED, null, "DELIVERY_DELIVERED")
                            : new ParcelEffect(P10, ParcelStatus.DELIVERED, null, "DELIVERY_DELIVERED");
            case D12 -> ret ? returnFailed(ParcelReturnCause.RETURN_DELIVERY_FAILED)
                            : new ParcelEffect(P11, ParcelStatus.RETURN_REQUESTED,
                                    ParcelReturnCause.NOT_COLLECTED_AT_RELAY, "DELIVERY_FAILED");
            case D20 -> afterPickup(ctx)
                    ? (ret ? returnFailed(ParcelReturnCause.RETURN_DELIVERY_FAILED)
                           : new ParcelEffect(P11, ParcelStatus.RETURN_REQUESTED,
                                   ParcelReturnCause.INCIDENT_UNRESOLVED, "DELIVERY_FAILED"))
                    // QO-5 default: not collected -> the parcel is free again (a new announcement can be made)
                    : backToCreated(ctx, "DELIVERY_FAILED");
            // D1 (creation: the parcel is created by P1 or is already in place), D13-D19 (incidents),
            // D21 (the parcel is already LOST/DAMAGED): the parcel does not change.
            default -> null;
        });
    }

    private static boolean afterPickup(Context ctx) {
        return collected(ctx.operationalState());
    }

    /** Before pickup: an assigned OUTBOUND/REDELIVERY parcel is free again (P3); otherwise unchanged. */
    private static ParcelEffect backToCreated(Context ctx, String trigger) {
        if (ctx.kind() == DeliveryKind.RETURN || ctx.parcelState() != ParcelStatus.ASSIGNED) {
            return null;
        }
        return new ParcelEffect(P3, ParcelStatus.CREATED, null, trigger);
    }

    /** P19: the return delivery was cancelled or failed; the parcel waits for a new return (QO-8). */
    private static ParcelEffect returnFailed(ParcelReturnCause cause) {
        return new ParcelEffect(P19, ParcelStatus.RETURN_REQUESTED, cause, cause.name());
    }
}
