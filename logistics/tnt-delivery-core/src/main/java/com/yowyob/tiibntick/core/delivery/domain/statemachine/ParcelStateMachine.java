package com.yowyob.tiibntick.core.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;

import java.util.EnumSet;
import java.util.Set;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus.*;

/**
 * The transition table of the parcel — the one that makes the rule (spec §1.2, rows P1–P20; decisions [B], [C bis],
 * QO-5, QO-8, QO-16). The parcel never changes on its own: each row names its cause (a delivery row of
 * {@link DeliveryStateMachine}, see {@link DeliveryParcelCorrespondence}, or the dispute/return use cases).
 */
public final class ParcelStateMachine {

    public static final String P1 = "P1";
    public static final String P2 = "P2";
    public static final String P3 = "P3";
    public static final String P4 = "P4";
    public static final String P5 = "P5";
    public static final String P6 = "P6";
    public static final String P7 = "P7";
    public static final String P8 = "P8";
    public static final String P9 = "P9";
    public static final String P10 = "P10";
    public static final String P11 = "P11";
    public static final String P12 = "P12";
    public static final String P13 = "P13";
    public static final String P14 = "P14";
    public static final String P15 = "P15";
    public static final String P16 = "P16";
    public static final String P17 = "P17";
    public static final String P18 = "P18";
    public static final String P19 = "P19";
    public static final String P20 = "P20";

    /** States a dispute can be opened from, and that are restored when it changes nothing (P12, P13). */
    public static final Set<ParcelStatus> DISPUTABLE = Set.copyOf(EnumSet.complementOf(
            EnumSet.of(UNDER_DISPUTE, LOST, DAMAGED)));

    /** States in which the parcel was collected (P11 sources). */
    public static final Set<ParcelStatus> COLLECTED_OUTBOUND =
            Set.copyOf(EnumSet.of(PICKED_UP, IN_TRANSIT, AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERY_FAILED));

    public static final TransitionTable<ParcelStatus> TABLE = TransitionTable.builder(ParcelStatus.class, CREATED)
            .create(P1, CREATED, "PARCEL_REGISTERED")
            .row(P2, Set.of(CREATED), ASSIGNED, "D2", "OUTBOUND or REDELIVERY delivery")
            .row(P3, Set.of(ASSIGNED), CREATED, "D3_D20_BEFORE_PICKUP", "—")
            .row(P4, Set.of(ASSIGNED), PICKED_UP, "D4", "OUTBOUND or REDELIVERY delivery")
            .row(P5, Set.of(PICKED_UP), IN_TRANSIT, "D5", "—")
            .row(P6, Set.of(AT_RELAY_HUB), IN_TRANSIT, "D6", "—")
            .row(P7, Set.of(IN_TRANSIT), AT_RELAY_HUB, "D7", "—")
            .row(P8, Set.of(AT_RELAY_HUB, DELIVERY_FAILED), OUT_FOR_DELIVERY, "D8", "—")
            .row(P9, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY), DELIVERY_FAILED, "D9", "—")
            .row(P10, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, AT_RELAY_HUB), DELIVERED, "D11",
                    "OUTBOUND or REDELIVERY delivery")
            .row(P11, COLLECTED_OUTBOUND, RETURN_REQUESTED, "D3_D10_D12_D20_AFTER_PICKUP", "—")
            .row(P12, DISPUTABLE, UNDER_DISPUTE, "DISPUTE_OPENED",
                    "no open dispute; DELIVERED/RETURNED: within the filing window")
            .restore(P13, Set.of(UNDER_DISPUTE), DISPUTABLE, "DISPUTE_RESTORE_PREVIOUS")
            .row(P14, Set.of(UNDER_DISPUTE), LOST, "DISPUTE_TOTAL_LOSS_COMPENSATED", "—")
            .row(P15, Set.of(UNDER_DISPUTE), DAMAGED, "DISPUTE_DAMAGE_COMPENSATED", "—")
            .row(P16, Set.of(UNDER_DISPUTE), RETURN_REQUESTED, "DISPUTE_RETURN_TO_SENDER_ORDERED", "—")
            .row(P17, Set.of(UNDER_DISPUTE), CREATED, "DISPUTE_REDELIVERY_ORDERED", "—")
            .row(P18, Set.of(RETURN_REQUESTED), RETURN_IN_TRANSIT, "D4_RETURN", "RETURN delivery")
            .row(P19, Set.of(RETURN_IN_TRANSIT), RETURN_REQUESTED, "RETURN_DELIVERY_CANCELLED_OR_FAILED",
                    "RETURN delivery (QO-8)")
            .row(P20, Set.of(RETURN_IN_TRANSIT), RETURNED, "D11_RETURN", "RETURN delivery")
            .build();

    private ParcelStateMachine() {
    }
}
