package com.yowyob.tiibntick.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelReturnCause;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence.Context;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence.ParcelEffect;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.TransitionTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery → parcel correspondence (spec §2.1, §2.2, [C bis]) without hole: for every delivery row, every delivery
 * kind, every source state (and every memorised operational state of an incident state), the parcel effect is a pair of
 * the parcel table from the parcel state that goes with the delivery state, and leads to the parcel state that goes
 * with the delivery target; and every delivery-driven parcel row is produced by at least one delivery row.
 */
class DeliveryParcelCorrespondenceTest {

    /** Parcel states consistent with a delivery operational state (spec §2.2 read backwards). */
    static Set<ParcelStatus> consistentParcel(DeliveryKind kind, DeliveryStatus state, boolean collected) {
        if (kind == DeliveryKind.RETURN) {
            return switch (state) {
                case CREATED, ASSIGNED, FAILED, CANCELLED -> EnumSet.of(ParcelStatus.RETURN_REQUESTED);
                case DELIVERED -> EnumSet.of(ParcelStatus.RETURNED);
                default -> EnumSet.of(ParcelStatus.RETURN_IN_TRANSIT);
            };
        }
        return switch (state) {
            case CREATED -> EnumSet.of(ParcelStatus.CREATED);
            case ASSIGNED -> EnumSet.of(ParcelStatus.ASSIGNED);
            case PICKED_UP -> EnumSet.of(ParcelStatus.PICKED_UP);
            case IN_TRANSIT -> EnumSet.of(ParcelStatus.IN_TRANSIT);
            case AT_RELAY_HUB -> EnumSet.of(ParcelStatus.AT_RELAY_HUB);
            case OUT_FOR_DELIVERY -> EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY);
            case DELIVERY_ATTEMPT_FAILED -> EnumSet.of(ParcelStatus.DELIVERY_FAILED);
            case DELIVERED -> EnumSet.of(ParcelStatus.DELIVERED);
            case FAILED, CANCELLED -> collected ? EnumSet.of(ParcelStatus.RETURN_REQUESTED)
                    : EnumSet.of(ParcelStatus.CREATED);
            default -> throw new IllegalArgumentException("not operational: " + state);
        };
    }

    record Case(DeliveryKind kind, String row, DeliveryStatus from, DeliveryStatus memorised, DeliveryStatus to) {
        DeliveryStatus operationalBefore() {
            return from.isIncidentState() ? memorised : from;
        }

        DeliveryStatus operationalAfter() {
            return to.isIncidentState() ? memorised : to;
        }
    }

    /** Every (kind, row, source, memorised, target) the delivery table allows. */
    static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        for (DeliveryKind kind : DeliveryKind.values()) {
            for (TransitionTable.Row<DeliveryStatus> row : DeliveryStateMachine.TABLE.rows()) {
                if (row.isCreation()) {
                    continue;
                }
                for (DeliveryStatus from : row.from()) {
                    for (DeliveryStatus to : row.to()) {
                        if (row.restoresMemorised()) {
                            cases.add(new Case(kind, row.id(), from, to, to));
                        } else if (from.isIncidentState()) {
                            Set<DeliveryStatus> memorisable = from == INCIDENT_HANDOVER
                                    || to == INCIDENT_HANDOVER
                                    ? DeliveryStateMachine.HANDOVER_SOURCES : DeliveryStateMachine.INTERRUPTIBLE;
                            memorisable.forEach(m -> cases.add(new Case(kind, row.id(), from, m, to)));
                        } else {
                            cases.add(new Case(kind, row.id(), from, to.isIncidentState() ? from : null, to));
                        }
                    }
                }
            }
        }
        return cases;
    }

    @Test
    @DisplayName("Every delivery transition keeps the parcel consistent, through a parcel-table pair (no hole)")
    void everyDeliveryTransitionHasAConsistentParcelEffect() {
        List<Case> cases = cases();
        assertThat(cases).hasSizeGreaterThan(100);
        for (Case c : cases) {
            if (c.row().equals(DeliveryStateMachine.D21)) {
                continue; // checked below: the parcel is already LOST/DAMAGED
            }
            boolean collectedBefore = DeliveryParcelCorrespondence.collected(c.operationalBefore());
            for (ParcelStatus parcel : consistentParcel(c.kind(), c.operationalBefore(), collectedBefore)) {
                DeliveryFailureReason reason = c.row().equals(DeliveryStateMachine.D10)
                        ? DeliveryFailureReason.CLIENT_REFUSED_RECEPTION : null;
                Optional<ParcelEffect> effect = DeliveryParcelCorrespondence.effectOf(
                        new Context(c.kind(), c.row(), c.operationalBefore(), parcel, reason));
                ParcelStatus after = effect.map(ParcelEffect::target).orElse(parcel);
                effect.ifPresent(e -> assertThat(ParcelStateMachine.TABLE.rowsFor(parcel, e.target()))
                        .as("%s: parcel %s -> %s by %s", c, parcel, e.target(), e.parcelRow())
                        .anyMatch(r -> r.id().equals(e.parcelRow())));
                assertThat(consistentParcel(c.kind(), c.operationalAfter(), collectedBefore))
                        .as("%s: parcel %s then %s (effect %s)", c, parcel, after, effect)
                        .contains(after);
            }
        }
    }

    @Test
    @DisplayName("D21 and the incident rows D13-D19 never move the parcel")
    void incidentAndLossRowsLeaveTheParcel() {
        for (Case c : cases()) {
            int n = Integer.parseInt(c.row().substring(1));
            if (n >= 13 && n <= 19 || n == 21) {
                assertThat(DeliveryParcelCorrespondence.effectOf(new Context(c.kind(), c.row(), c.operationalBefore(),
                        ParcelStatus.IN_TRANSIT, null))).as("%s", c).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("Every delivery-driven parcel row (P2-P11, P18-P20) is produced by a delivery row")
    void everyParcelRowHasADeliveryCause() {
        Set<String> produced = new TreeSet<>();
        for (Case c : cases()) {
            boolean collected = DeliveryParcelCorrespondence.collected(c.operationalBefore());
            for (ParcelStatus parcel : consistentParcel(c.kind(), c.operationalBefore(), collected)) {
                for (DeliveryFailureReason reason : new DeliveryFailureReason[] {null,
                        DeliveryFailureReason.CLIENT_REFUSED_RECEPTION}) {
                    DeliveryParcelCorrespondence.effectOf(new Context(c.kind(), c.row(), c.operationalBefore(),
                            parcel, reason)).ifPresent(e -> produced.add(e.parcelRow()));
                }
            }
        }
        assertThat(produced).containsExactlyInAnyOrder("P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9", "P10",
                "P11", "P18", "P19", "P20");
    }

    @Test
    @DisplayName("Return causes: refusal, max attempts, not collected, incident, cancellation after pickup")
    void returnCauses() {
        assertThat(effect(DeliveryKind.OUTBOUND, "D10", OUT_FOR_DELIVERY, ParcelStatus.OUT_FOR_DELIVERY,
                DeliveryFailureReason.CLIENT_REFUSED_RECEPTION).returnCause())
                .isEqualTo(ParcelReturnCause.CLIENT_REFUSED_RECEPTION);
        assertThat(effect(DeliveryKind.OUTBOUND, "D10", OUT_FOR_DELIVERY, ParcelStatus.OUT_FOR_DELIVERY,
                DeliveryFailureReason.MAX_ATTEMPTS_REACHED).returnCause())
                .isEqualTo(ParcelReturnCause.MAX_ATTEMPTS_REACHED);
        assertThat(effect(DeliveryKind.OUTBOUND, "D12", AT_RELAY_HUB, ParcelStatus.AT_RELAY_HUB, null)
                .returnCause()).isEqualTo(ParcelReturnCause.NOT_COLLECTED_AT_RELAY);
        assertThat(effect(DeliveryKind.OUTBOUND, "D20", IN_TRANSIT, ParcelStatus.IN_TRANSIT, null).returnCause())
                .isEqualTo(ParcelReturnCause.INCIDENT_UNRESOLVED);
        assertThat(effect(DeliveryKind.OUTBOUND, "D3", PICKED_UP, ParcelStatus.PICKED_UP, null).returnCause())
                .isEqualTo(ParcelReturnCause.SENDER_CANCELLED_AFTER_PICKUP);
        assertThat(effect(DeliveryKind.RETURN, "D3", PICKED_UP, ParcelStatus.RETURN_IN_TRANSIT, null).parcelRow())
                .isEqualTo("P19");
        assertThat(effect(DeliveryKind.OUTBOUND, "D20", ASSIGNED, ParcelStatus.ASSIGNED, null).parcelRow())
                .isEqualTo("P3");
    }

    private static ParcelEffect effect(DeliveryKind kind, String row, DeliveryStatus op, ParcelStatus parcel,
                                      DeliveryFailureReason reason) {
        return DeliveryParcelCorrespondence.effectOf(new Context(kind, row, op, parcel, reason)).orElseThrow();
    }
}
