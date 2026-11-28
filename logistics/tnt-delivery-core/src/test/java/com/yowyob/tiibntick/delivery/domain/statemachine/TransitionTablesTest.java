package com.yowyob.tiibntick.delivery.domain.statemachine;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.AnnouncementStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.TransitionTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three transition tables against the spec (cible-evenements-colis-livraison.md §1.1, §1.2, §1.5), on the full
 * product states × states: every pair listed in the spec is allowed, every other pair is refused. The expected pairs
 * below are transcribed from the spec tables, not from the code. Plus the structural rules of [C bis]: no unreachable
 * state, no dead end, terminal/active helpers derived from the table, one event name per target state.
 */
class TransitionTablesTest {

    // ── spec oracles (one entry per row: sources > targets) ──────────────────────────────────────

    private static final String INTERRUPTIBLE = "ASSIGNED,PICKED_UP,IN_TRANSIT,OUT_FOR_DELIVERY,DELIVERY_ATTEMPT_FAILED";
    private static final String NON_TERMINAL_DELIVERY = "CREATED,ASSIGNED,PICKED_UP,IN_TRANSIT,AT_RELAY_HUB,"
            + "OUT_FOR_DELIVERY,DELIVERY_ATTEMPT_FAILED,INCIDENT_REPORTED,PAUSED_BY_INCIDENT,INCIDENT_HANDOVER";

    static final List<String> DELIVERY_SPEC = List.of(
            "CREATED > ASSIGNED",                                                       // D2
            "CREATED,ASSIGNED,PICKED_UP,PAUSED_BY_INCIDENT > CANCELLED",               // D3
            "ASSIGNED > PICKED_UP",                                                     // D4
            "PICKED_UP > IN_TRANSIT",                                                   // D5
            "AT_RELAY_HUB > IN_TRANSIT",                                                // D6
            "IN_TRANSIT > AT_RELAY_HUB",                                                // D7
            "AT_RELAY_HUB,DELIVERY_ATTEMPT_FAILED > OUT_FOR_DELIVERY",                  // D8
            "IN_TRANSIT,OUT_FOR_DELIVERY > DELIVERY_ATTEMPT_FAILED",                    // D9
            "IN_TRANSIT,OUT_FOR_DELIVERY > FAILED",                                     // D10
            "IN_TRANSIT,OUT_FOR_DELIVERY,AT_RELAY_HUB > DELIVERED",                     // D11
            "AT_RELAY_HUB > FAILED",                                                    // D12
            INTERRUPTIBLE + " > INCIDENT_REPORTED",                                     // D13
            "INCIDENT_REPORTED > " + INTERRUPTIBLE,                                     // D14
            "INCIDENT_REPORTED," + INTERRUPTIBLE + " > PAUSED_BY_INCIDENT",             // D15
            "PAUSED_BY_INCIDENT > " + INTERRUPTIBLE,                                    // D16
            "PAUSED_BY_INCIDENT > INCIDENT_HANDOVER",                                   // D17
            "INCIDENT_HANDOVER > " + INTERRUPTIBLE,                                     // D18
            "INCIDENT_HANDOVER > PAUSED_BY_INCIDENT",                                   // D19
            "PAUSED_BY_INCIDENT,INCIDENT_HANDOVER > FAILED",                            // D20
            NON_TERMINAL_DELIVERY + " > FAILED");                                       // D21

    private static final String DISPUTABLE = "CREATED,ASSIGNED,PICKED_UP,IN_TRANSIT,AT_RELAY_HUB,OUT_FOR_DELIVERY,"
            + "DELIVERED,DELIVERY_FAILED,RETURNED,RETURN_REQUESTED,RETURN_IN_TRANSIT";

    static final List<String> PARCEL_SPEC = List.of(
            "CREATED > ASSIGNED",                                                       // P2
            "ASSIGNED > CREATED",                                                       // P3
            "ASSIGNED > PICKED_UP",                                                     // P4
            "PICKED_UP > IN_TRANSIT",                                                   // P5
            "AT_RELAY_HUB > IN_TRANSIT",                                                // P6
            "IN_TRANSIT > AT_RELAY_HUB",                                                // P7
            "AT_RELAY_HUB,DELIVERY_FAILED > OUT_FOR_DELIVERY",                          // P8
            "IN_TRANSIT,OUT_FOR_DELIVERY > DELIVERY_FAILED",                            // P9
            "IN_TRANSIT,OUT_FOR_DELIVERY,AT_RELAY_HUB > DELIVERED",                     // P10
            "PICKED_UP,IN_TRANSIT,AT_RELAY_HUB,OUT_FOR_DELIVERY,DELIVERY_FAILED > RETURN_REQUESTED", // P11
            DISPUTABLE + " > UNDER_DISPUTE",                                            // P12
            "UNDER_DISPUTE > " + DISPUTABLE,                                            // P13
            "UNDER_DISPUTE > LOST",                                                     // P14
            "UNDER_DISPUTE > DAMAGED",                                                  // P15
            "UNDER_DISPUTE > RETURN_REQUESTED",                                         // P16
            "UNDER_DISPUTE > CREATED",                                                  // P17
            "RETURN_REQUESTED > RETURN_IN_TRANSIT",                                     // P18
            "RETURN_IN_TRANSIT > RETURN_REQUESTED",                                     // P19
            "RETURN_IN_TRANSIT > RETURNED");                                            // P20

    static final List<String> ANNOUNCEMENT_SPEC = List.of(
            "DRAFT > PUBLISHED",                                                        // A2
            "PUBLISHED > IN_NEGOTIATION",                                               // A3
            "PUBLISHED,IN_NEGOTIATION > ASSIGNED",                                      // A4
            "DRAFT,PUBLISHED,IN_NEGOTIATION > CANCELLED",                               // A5
            "ASSIGNED > CANCELLED",                                                     // A6
            "ASSIGNED > COMPLETED");                                                    // A7

    static final List<String> RESPONSE_SPEC = List.of("SENT > ACCEPTED,REJECTED,CANCELLED"); // R2, R3, R4

    // ── product states × states ───────────────────────────────────────────────────────────────────

    static Stream<Arguments> deliveryPairs() {
        return pairs(DeliveryStatus.class, DELIVERY_SPEC);
    }

    static Stream<Arguments> parcelPairs() {
        return pairs(ParcelStatus.class, PARCEL_SPEC);
    }

    static Stream<Arguments> announcementPairs() {
        return pairs(AnnouncementStatus.class, ANNOUNCEMENT_SPEC);
    }

    static Stream<Arguments> responsePairs() {
        return pairs(ResponseStatus.class, RESPONSE_SPEC);
    }

    @ParameterizedTest(name = "delivery {0} -> {1}: allowed={2}")
    @MethodSource("deliveryPairs")
    @DisplayName("Delivery table = spec §1.1 on all 13 x 13 pairs")
    void deliveryTableMatchesSpec(DeliveryStatus from, DeliveryStatus to, boolean expected) {
        assertThat(DeliveryStateMachine.TABLE.isAllowed(from, to)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parcel {0} -> {1}: allowed={2}")
    @MethodSource("parcelPairs")
    @DisplayName("Parcel table = spec §1.2 on all 14 x 14 pairs")
    void parcelTableMatchesSpec(ParcelStatus from, ParcelStatus to, boolean expected) {
        assertThat(ParcelStateMachine.TABLE.isAllowed(from, to)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "announcement {0} -> {1}: allowed={2}")
    @MethodSource("announcementPairs")
    @DisplayName("Announcement table = spec §1.5 on all 6 x 6 pairs")
    void announcementTableMatchesSpec(AnnouncementStatus from, AnnouncementStatus to, boolean expected) {
        assertThat(AnnouncementStateMachine.TABLE.isAllowed(from, to)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "response {0} -> {1}: allowed={2}")
    @MethodSource("responsePairs")
    @DisplayName("Response table = spec §1.5 on all 4 x 4 pairs")
    void responseTableMatchesSpec(ResponseStatus from, ResponseStatus to, boolean expected) {
        assertThat(AnnouncementStateMachine.RESPONSE_TABLE.isAllowed(from, to)).isEqualTo(expected);
    }

    // ── structural rules [C bis] ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Every state of every table is reachable from the initial state")
    void noUnreachableState() {
        assertThat(DeliveryStateMachine.TABLE.reachableFromInitial()).containsExactlyInAnyOrder(DeliveryStatus.values());
        assertThat(ParcelStateMachine.TABLE.reachableFromInitial()).containsExactlyInAnyOrder(ParcelStatus.values());
        assertThat(AnnouncementStateMachine.TABLE.reachableFromInitial())
                .containsExactlyInAnyOrder(AnnouncementStatus.values());
        assertThat(AnnouncementStateMachine.RESPONSE_TABLE.reachableFromInitial())
                .containsExactlyInAnyOrder(ResponseStatus.values());
    }

    @Test
    @DisplayName("Terminal states are exactly the states without exit; every non-terminal state reaches a terminal one")
    void noDeadEnd() {
        assertThat(DeliveryStateMachine.TABLE.terminalStates()).containsExactlyInAnyOrder(
                DeliveryStatus.DELIVERED, DeliveryStatus.FAILED, DeliveryStatus.CANCELLED);
        assertThat(ParcelStateMachine.TABLE.terminalStates()).containsExactlyInAnyOrder(
                ParcelStatus.LOST, ParcelStatus.DAMAGED);
        assertThat(AnnouncementStateMachine.TABLE.terminalStates()).containsExactlyInAnyOrder(
                AnnouncementStatus.CANCELLED, AnnouncementStatus.COMPLETED);
        assertThat(AnnouncementStateMachine.RESPONSE_TABLE.terminalStates()).containsExactlyInAnyOrder(
                ResponseStatus.ACCEPTED, ResponseStatus.REJECTED, ResponseStatus.CANCELLED);
        assertReachesTerminal(DeliveryStateMachine.TABLE, DeliveryStatus.class);
        assertReachesTerminal(ParcelStateMachine.TABLE, ParcelStatus.class);
        assertReachesTerminal(AnnouncementStateMachine.TABLE, AnnouncementStatus.class);
        assertReachesTerminal(AnnouncementStateMachine.RESPONSE_TABLE, ResponseStatus.class);
    }

    @Test
    @DisplayName("isTerminal / isActive / isIncidentState / isClosed are derived from the tables")
    void helpersDerivedFromTables() {
        for (DeliveryStatus s : DeliveryStatus.values()) {
            assertThat(s.isTerminal()).as("isTerminal %s", s)
                    .isEqualTo(DeliveryStateMachine.TABLE.targetsFrom(s).isEmpty());
            assertThat(s.isActive()).as("isActive %s", s).isEqualTo(!s.isTerminal() && s != DeliveryStatus.CREATED);
            assertThat(s.isIncidentState()).as("isIncidentState %s", s)
                    .isEqualTo(EnumSet.of(DeliveryStatus.INCIDENT_REPORTED, DeliveryStatus.PAUSED_BY_INCIDENT,
                            DeliveryStatus.INCIDENT_HANDOVER).contains(s));
        }
        assertThat(DeliveryStatus.terminalStates()).isEqualTo(DeliveryStateMachine.TABLE.terminalStates());
        for (ParcelStatus s : ParcelStatus.values()) {
            assertThat(s.isTerminal()).as("isTerminal %s", s)
                    .isEqualTo(ParcelStateMachine.TABLE.targetsFrom(s).isEmpty());
            assertThat(s.isClosed()).as("isClosed %s", s)
                    .isEqualTo(ParcelStateMachine.TABLE.targetsFrom(s).equals(Set.of(ParcelStatus.UNDER_DISPUTE)));
        }
        assertThat(Arrays.stream(ParcelStatus.values()).filter(ParcelStatus::isClosed))
                .containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.RETURNED);
        for (AnnouncementStatus s : AnnouncementStatus.values()) {
            assertThat(s.isTerminal()).isEqualTo(AnnouncementStateMachine.TABLE.targetsFrom(s).isEmpty());
            assertThat(s.isActive()).as("isActive %s", s)
                    .isEqualTo(AnnouncementStateMachine.TABLE.rowsFor(s, AnnouncementStatus.ASSIGNED).size() == 1);
        }
        for (ResponseStatus s : ResponseStatus.values()) {
            assertThat(s.isTerminal()).isEqualTo(AnnouncementStateMachine.RESPONSE_TABLE.targetsFrom(s).isEmpty());
            assertThat(s.isActive()).isEqualTo(!s.isTerminal());
        }
    }

    @Test
    @DisplayName("Row ids are D1..D21, P1..P20, A1..A7, R1..R4, without gap")
    void rowIdsAreComplete() {
        assertThat(DeliveryStateMachine.TABLE.rows()).extracting(TransitionTable.Row::id)
                .containsExactlyElementsOf(ids("D", 21));
        assertThat(ParcelStateMachine.TABLE.rows()).extracting(TransitionTable.Row::id)
                .containsExactlyElementsOf(ids("P", 20));
        assertThat(AnnouncementStateMachine.TABLE.rows()).extracting(TransitionTable.Row::id)
                .containsExactlyElementsOf(ids("A", 7));
        assertThat(AnnouncementStateMachine.RESPONSE_TABLE.rows()).extracting(TransitionTable.Row::id)
                .containsExactlyElementsOf(ids("R", 4));
    }

    @Test
    @DisplayName("One event name per target state: <Aggregate><PascalCase(state)>, all distinct")
    void oneEventNamePerState() {
        assertThat(eventNames(TntAggregateType.DELIVERY, DeliveryStatus.class)).containsExactly(
                "DeliveryCreated", "DeliveryAssigned", "DeliveryPickedUp", "DeliveryInTransit", "DeliveryAtRelayHub",
                "DeliveryOutForDelivery", "DeliveryAttemptFailed", "DeliveryDelivered", "DeliveryFailed",
                "DeliveryCancelled", "DeliveryIncidentReported", "DeliveryPausedByIncident",
                "DeliveryIncidentHandover");
        assertThat(eventNames(TntAggregateType.PARCEL, ParcelStatus.class)).containsExactly(
                "ParcelCreated", "ParcelAssigned", "ParcelPickedUp", "ParcelInTransit", "ParcelAtRelayHub",
                "ParcelOutForDelivery", "ParcelDelivered", "ParcelDeliveryFailed", "ParcelUnderDispute",
                "ParcelReturned", "ParcelReturnRequested", "ParcelReturnInTransit", "ParcelLost", "ParcelDamaged");
        assertThat(eventNames(TntAggregateType.ANNOUNCEMENT, AnnouncementStatus.class)).containsExactly(
                "AnnouncementDraft", "AnnouncementPublished", "AnnouncementInNegotiation", "AnnouncementAssigned",
                "AnnouncementCompleted", "AnnouncementCancelled");
        assertThat(eventNames(TntAggregateType.DELIVERY, DeliveryStatus.class)).doesNotHaveDuplicates();
        assertThat(eventNames(TntAggregateType.PARCEL, ParcelStatus.class)).doesNotHaveDuplicates();
        assertThat(eventNames(TntAggregateType.ANNOUNCEMENT, AnnouncementStatus.class)).doesNotHaveDuplicates();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static <S extends Enum<S>> Stream<Arguments> pairs(Class<S> type, List<String> spec) {
        Set<String> allowed = new HashSet<>();
        for (String line : spec) {
            String[] parts = line.split(">");
            for (String from : parts[0].trim().split(",")) {
                for (String to : parts[1].trim().split(",")) {
                    Enum.valueOf(type, from.trim());
                    Enum.valueOf(type, to.trim());
                    allowed.add(from.trim() + ">" + to.trim());
                }
            }
        }
        List<Arguments> out = new ArrayList<>();
        for (S from : type.getEnumConstants()) {
            for (S to : type.getEnumConstants()) {
                out.add(Arguments.of(from, to, allowed.contains(from.name() + ">" + to.name())));
            }
        }
        return out.stream();
    }

    private static <S extends Enum<S>> void assertReachesTerminal(TransitionTable<S> table, Class<S> type) {
        Set<S> terminal = table.terminalStates();
        for (S start : type.getEnumConstants()) {
            Set<S> seen = EnumSet.noneOf(type);
            List<S> queue = new ArrayList<>(List.of(start));
            boolean reached = terminal.contains(start);
            while (!queue.isEmpty() && !reached) {
                S s = queue.removeFirst();
                for (S next : table.targetsFrom(s)) {
                    if (terminal.contains(next)) {
                        reached = true;
                    }
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            assertThat(reached).as("%s reaches a terminal state", start).isTrue();
        }
    }

    private static List<String> ids(String prefix, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add(prefix + i);
        }
        return ids;
    }

    private static <S extends Enum<S>> List<String> eventNames(TntAggregateType type, Class<S> states) {
        return Arrays.stream(states.getEnumConstants()).map(s -> type.eventTypeFor(s.name())).toList();
    }
}
