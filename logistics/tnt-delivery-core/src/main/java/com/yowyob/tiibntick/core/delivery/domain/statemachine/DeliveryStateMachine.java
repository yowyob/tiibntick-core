package com.yowyob.tiibntick.core.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;

import java.util.EnumSet;
import java.util.Set;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.*;

/**
 * The transition table of the delivery — the one that makes the rule (spec {@code cible-evenements-colis-livraison.md}
 * §1.1, rows D1–D21; decisions [C], [C bis], QO-1, QO-3, QO-5, QO-6). Javadoc, {@link DeliveryStatus} helpers, the
 * documentation and the tests are derived from or checked against {@link #TABLE}.
 *
 * <p>Guards that depend on data (attempt counter, codes, frozen parcel, memorised state) are checked by the
 * {@code Delivery} aggregate; the table answers which pairs exist at all. Every accepted transition emits exactly one
 * {@code Delivery<PascalCase(target)>} event.
 */
public final class DeliveryStateMachine {

    public static final String D1 = "D1";
    public static final String D2 = "D2";
    public static final String D3 = "D3";
    public static final String D4 = "D4";
    public static final String D5 = "D5";
    public static final String D6 = "D6";
    public static final String D7 = "D7";
    public static final String D8 = "D8";
    public static final String D9 = "D9";
    public static final String D10 = "D10";
    public static final String D11 = "D11";
    public static final String D12 = "D12";
    public static final String D13 = "D13";
    public static final String D14 = "D14";
    public static final String D15 = "D15";
    public static final String D16 = "D16";
    public static final String D17 = "D17";
    public static final String D18 = "D18";
    public static final String D19 = "D19";
    public static final String D20 = "D20";
    public static final String D21 = "D21";

    /** Operational states an incident can interrupt and that are restored afterwards (rows D13–D18). */
    public static final Set<DeliveryStatus> INTERRUPTIBLE =
            Set.copyOf(EnumSet.of(ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED));

    /** Memorised states for which a replacement courier takes the parcel over (row D17). */
    public static final Set<DeliveryStatus> HANDOVER_SOURCES =
            Set.copyOf(EnumSet.of(PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED));

    /** Every non-terminal state (row D21: the parcel became LOST or DAMAGED). */
    private static final Set<DeliveryStatus> NON_TERMINAL = Set.copyOf(EnumSet.of(CREATED, ASSIGNED, PICKED_UP,
            IN_TRANSIT, AT_RELAY_HUB, OUT_FOR_DELIVERY, DELIVERY_ATTEMPT_FAILED, INCIDENT_REPORTED, PAUSED_BY_INCIDENT,
            INCIDENT_HANDOVER));

    public static final TransitionTable<DeliveryStatus> TABLE = TransitionTable.builder(DeliveryStatus.class, CREATED)
            .create(D1, CREATED, "CREATE_DELIVERY")
            .row(D2, Set.of(CREATED), ASSIGNED, "ASSIGN_COURIER", "courier APPROVED")
            .row(D3, Set.of(CREATED, ASSIGNED, PICKED_UP, PAUSED_BY_INCIDENT), CANCELLED, "CANCEL_DELIVERY",
                    "reason required (QO-6)")
            .row(D4, Set.of(ASSIGNED), PICKED_UP, "CONFIRM_PICKUP", "valid hand-over code")
            .row(D5, Set.of(PICKED_UP), IN_TRANSIT, "START_TRANSIT", "—")
            .row(D6, Set.of(AT_RELAY_HUB), IN_TRANSIT, "RESUME_FROM_RELAY_HUB", "next stop is another relay")
            .row(D7, Set.of(IN_TRANSIT), AT_RELAY_HUB, "DEPOSIT_AT_RELAY_HUB", "relay active")
            .row(D8, Set.of(AT_RELAY_HUB, DELIVERY_ATTEMPT_FAILED), OUT_FOR_DELIVERY, "START_FINAL_LEG",
                    "from DELIVERY_ATTEMPT_FAILED: attemptCount < maxAttempts")
            .row(D9, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY), DELIVERY_ATTEMPT_FAILED, "RECORD_FAILED_ATTEMPT",
                    "attempt reason other than refusal and attemptCount < maxAttempts after increment")
            .row(D10, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY), FAILED, "RECORD_FAILED_ATTEMPT",
                    "refusal by the recipient, or attemptCount >= maxAttempts after increment")
            .row(D11, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, AT_RELAY_HUB), DELIVERED, "COMPLETE_DELIVERY",
                    "valid OTP / collection code; delivery not frozen by a dispute")
            .row(D12, Set.of(AT_RELAY_HUB), FAILED, "RELAY_STORAGE_EXPIRED",
                    "relay is the destination and storage expired")
            .row(D13, INTERRUPTIBLE, INCIDENT_REPORTED, "INCIDENT_REPORTED", "memorise the operational state")
            .restore(D14, Set.of(INCIDENT_REPORTED), INTERRUPTIBLE, "INCIDENT_NOT_BLOCKING")
            .row(D15, union(INTERRUPTIBLE, Set.of(INCIDENT_REPORTED)), PAUSED_BY_INCIDENT, "INCIDENT_BLOCKING",
                    "blocking incident; memorise the operational state")
            .restore(D16, Set.of(PAUSED_BY_INCIDENT), INTERRUPTIBLE, "INCIDENT_RESOLVED")
            .row(D17, Set.of(PAUSED_BY_INCIDENT), INCIDENT_HANDOVER, "START_HANDOVER",
                    "memorised state in HANDOVER_SOURCES (the courier holds the parcel)")
            .restore(D18, Set.of(INCIDENT_HANDOVER), INTERRUPTIBLE, "HANDOVER_COMPLETED")
            .row(D19, Set.of(INCIDENT_HANDOVER), PAUSED_BY_INCIDENT, "HANDOVER_ABORTED", "—")
            .row(D20, Set.of(PAUSED_BY_INCIDENT, INCIDENT_HANDOVER), FAILED, "INCIDENT_UNRESOLVED", "—")
            .row(D21, NON_TERMINAL, FAILED, "PARCEL_LOST_OR_DAMAGED", "parcel LOST or DAMAGED")
            .build();

    private DeliveryStateMachine() {
    }

    private static Set<DeliveryStatus> union(Set<DeliveryStatus> a, Set<DeliveryStatus> b) {
        EnumSet<DeliveryStatus> all = EnumSet.noneOf(DeliveryStatus.class);
        all.addAll(a);
        all.addAll(b);
        return all;
    }
}
