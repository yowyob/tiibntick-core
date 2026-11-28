package com.yowyob.tiibntick.delivery.domain.statemachine;

import com.yowyob.tiibntick.common.kafka.TntAggregateType;
import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryFrozenByDisputeException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryParcelCorrespondence;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@link Delivery} aggregate on the full product states × states (13 × 13): from every state, every operation
 * that can reach a target is tried on a fresh delivery whose parcel is consistent; the target is reached iff the table
 * allows the pair, the refusal is a domain exception, and an accepted transition records exactly one
 * {@code Delivery<Target>} event of a row of the table and leaves the parcel consistent.
 */
class DeliveryAggregateTransitionsTest {

    private static final UUID HUB = UUID.randomUUID();

    /** The operations of the aggregate, by the state they lead to. */
    static Map<DeliveryStatus, List<Consumer<Delivery>>> operations() {
        EtaEstimate eta = EtaEstimate.of(Instant.now().plusSeconds(1800), 5.0, 30);
        Map<DeliveryStatus, List<Consumer<Delivery>>> ops = new java.util.EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus s : DeliveryStatus.values()) {
            ops.put(s, new ArrayList<>());
        }
        Consumer<Delivery> resume = d -> d.resumeAfterIncident(null, UUID.randomUUID(), null);
        ops.get(ASSIGNED).add(d -> d.assignDeliveryPerson(UUID.randomUUID(), null, 1.0, null));
        ops.get(CANCELLED).add(d -> d.cancel("changed plans"));
        ops.get(PICKED_UP).add(Delivery::confirmPickup);
        ops.get(IN_TRANSIT).add(d -> d.startTransit(eta));
        ops.get(IN_TRANSIT).add(d -> d.resumeFromRelayHub(eta));
        ops.get(AT_RELAY_HUB).add(d -> d.depositAtRelayHub(RelayDeposit.at(HUB)));
        ops.get(OUT_FOR_DELIVERY).add(d -> d.startFinalLeg(eta, 3));
        ops.get(DELIVERY_ATTEMPT_FAILED).add(d -> d.recordFailedAttempt(
                DeliveryFailureReason.CLIENT_ABSENT_AT_DELIVERY, 3));
        ops.get(DELIVERED).add(d -> d.complete(DeliveryCost.fromDistanceOnly(BigDecimal.TEN, "XAF")));
        ops.get(FAILED).add(d -> d.recordFailedAttempt(DeliveryFailureReason.CLIENT_REFUSED_RECEPTION, 3));
        ops.get(FAILED).add(Delivery::expireRelayStorage);
        ops.get(FAILED).add(d -> d.failAfterIncident(null));
        ops.get(FAILED).add(d -> d.failForParcelLoss(DeliveryFailureReason.PARCEL_LOST, null));
        ops.get(INCIDENT_REPORTED).add(d -> d.markIncidentReported(UUID.randomUUID()));
        ops.get(PAUSED_BY_INCIDENT).add(d -> d.pauseForIncident(UUID.randomUUID()));
        ops.get(PAUSED_BY_INCIDENT).add(Delivery::abortHandover);
        ops.get(INCIDENT_HANDOVER).add(Delivery::startHandover);
        for (DeliveryStatus s : DeliveryStateMachine.INTERRUPTIBLE) {
            ops.get(s).add(resume);
        }
        return ops;
    }

    static Stream<Arguments> pairs() {
        List<Arguments> args = new ArrayList<>();
        for (DeliveryStatus from : DeliveryStatus.values()) {
            for (DeliveryStatus to : DeliveryStatus.values()) {
                args.add(Arguments.of(from, to));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("pairs")
    @DisplayName("Aggregate accepts exactly the pairs of the table (13 x 13)")
    void aggregateFollowsTheTable(DeliveryStatus from, DeliveryStatus to) {
        boolean allowed = DeliveryStateMachine.TABLE.isAllowed(from, to);
        boolean reached = false;
        for (Consumer<Delivery> op : operations().get(to)) {
            DeliveryStatus memorised = DeliveryStateMachine.INTERRUPTIBLE.contains(to) ? to : IN_TRANSIT;
            Delivery d = deliveryIn(from, memorised);
            try {
                op.accept(d);
            } catch (DeliveryDomainException refused) {
                assertThat(d.getStatus()).as("refused operation leaves the state").isEqualTo(from);
                assertThat(d.getAggregateEvents()).as("refused operation records nothing").isEmpty();
                continue;
            }
            if (d.getStatus() != to) {
                continue;
            }
            reached = true;
            List<AggregateEvent> events = d.getAggregateEvents();
            assertThat(events).hasSize(1);
            AggregateEvent e = events.getFirst();
            assertThat(e.eventType()).isEqualTo(TntAggregateType.DELIVERY.eventTypeFor(to.name()));
            assertThat(e.previousStatus()).isEqualTo(from.name());
            assertThat(DeliveryStateMachine.TABLE.rowsFor(from, to)).anyMatch(r -> r.id().equals(e.row()));
            DeliveryStatus opAfter = to.isIncidentState() ? d.getPreviousStatusBeforePause() : to;
            DeliveryStatus opBefore = from.isIncidentState() ? memorised : from;
            if (!e.row().equals(DeliveryStateMachine.D21)) {
                assertThat(DeliveryParcelCorrespondenceTest.consistentParcel(DeliveryKind.OUTBOUND, opAfter,
                        DeliveryParcelCorrespondence.collected(opBefore)))
                        .as("parcel after %s", e.row()).contains(d.getParcel().getStatus());
            }
            if (to.isTerminal()) {
                assertThat(d.getParcel().getCurrentDeliveryId()).as("parcel released").isNull();
            }
        }
        assertThat(reached).as("%s -> %s reached", from, to).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "frozen {0}")
    @MethodSource("nonTerminal")
    @DisplayName("A dispute that freezes the delivery refuses every row except D20 and D21 (QO-7)")
    void frozenByDispute(DeliveryStatus from) {
        for (Map.Entry<DeliveryStatus, List<Consumer<Delivery>>> entry : operations().entrySet()) {
            for (Consumer<Delivery> op : entry.getValue()) {
                Delivery d = deliveryIn(from, IN_TRANSIT);
                d.getParcel().openDispute(UUID.randomUUID(), true, true, TntEventActor.system(), null, null);
                d.clearDomainEvents();
                try {
                    op.accept(d);
                } catch (DeliveryFrozenByDisputeException frozen) {
                    continue;
                } catch (DeliveryDomainException otherRefusal) {
                    continue;
                }
                assertThat(d.getAggregateEvents().getFirst().row()).as("only D20/D21 while frozen")
                        .isIn(DeliveryStateMachine.D20, DeliveryStateMachine.D21);
            }
        }
    }

    static Stream<DeliveryStatus> nonTerminal() {
        return Stream.of(DeliveryStatus.values()).filter(s -> !s.isTerminal());
    }

    @Test
    @DisplayName("D9 then D10 at the maximum of attempts; D8 refused once the maximum is reached")
    void attemptsCounter() {
        Delivery d = deliveryIn(OUT_FOR_DELIVERY, null);
        EtaEstimate eta = EtaEstimate.of(Instant.now().plusSeconds(600), 1.0, 10);
        assertThat(d.recordFailedAttempt(DeliveryFailureReason.CLIENT_ABSENT_AT_DELIVERY, 3))
                .isEqualTo(DELIVERY_ATTEMPT_FAILED);
        d.startFinalLeg(eta, 3);
        assertThat(d.recordFailedAttempt(DeliveryFailureReason.CLIENT_PHONE_UNREACHABLE, 3))
                .isEqualTo(DELIVERY_ATTEMPT_FAILED);
        d.startFinalLeg(eta, 3);
        assertThat(d.recordFailedAttempt(DeliveryFailureReason.CLIENT_ABSENT_AT_DELIVERY, 3)).isEqualTo(FAILED);
        assertThat(d.getAttemptCount()).isEqualTo(3);
        assertThat(d.getFailureReason()).isEqualTo(DeliveryFailureReason.MAX_ATTEMPTS_REACHED);
        assertThat(d.getParcel().getStatus()).isEqualTo(ParcelStatus.RETURN_REQUESTED);
        assertThat(parcelEvents(d)).contains("ParcelReturnRequested");

        // the maximum is read at each transition: lowered to the attempts already made, no new attempt (D8 guard)
        Delivery lowered = deliveryIn(OUT_FOR_DELIVERY, null);
        lowered.recordFailedAttempt(DeliveryFailureReason.CLIENT_ABSENT_AT_DELIVERY, 3);
        assertThatThrownBy(() -> lowered.startFinalLeg(eta, 1)).isInstanceOf(DeliveryDomainException.class);
        assertThat(lowered.getStatus()).isEqualTo(DELIVERY_ATTEMPT_FAILED);
    }

    @Test
    @DisplayName("Not an attempt reason / no cancellation reason / D18 without courier are refused")
    void dataGuards() {
        assertThatThrownBy(() -> deliveryIn(OUT_FOR_DELIVERY, null)
                .recordFailedAttempt(DeliveryFailureReason.PARCEL_LOST, 3))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> deliveryIn(ASSIGNED, null).cancel(" "))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> deliveryIn(INCIDENT_HANDOVER, IN_TRANSIT).resumeAfterIncident(null, null, null))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> deliveryIn(PAUSED_BY_INCIDENT, ASSIGNED).startHandover())
                .isInstanceOf(DeliveryDomainException.class);
    }

    @Test
    @DisplayName("D18: the replacement courier holds the parcel (ParcelCustodyChanged)")
    void handoverChangesCustody() {
        Delivery d = deliveryIn(INCIDENT_HANDOVER, IN_TRANSIT);
        UUID replacement = UUID.randomUUID();
        assertThat(d.resumeAfterIncident(null, replacement, null)).isEqualTo(IN_TRANSIT);
        assertThat(d.getDeliveryPersonId()).isEqualTo(replacement);
        assertThat(d.getParcel().getCustody()).isEqualTo(ParcelCustody.courier(replacement));
        assertThat(parcelEvents(d)).containsExactly("ParcelCustodyChanged");
        assertThat(d.getAggregateEvents().getFirst().trigger()).isEqualTo("HANDOVER_COMPLETED");
    }

    @Test
    @DisplayName("Non-freezing dispute (delay): the delivery continues, the parcel memorised state moves")
    void nonFreezingDisputeMovesMemorisedState() {
        Delivery d = deliveryIn(OUT_FOR_DELIVERY, null);
        d.getParcel().openDispute(UUID.randomUUID(), false, true, TntEventActor.system(), null, null);
        d.clearDomainEvents();
        d.complete(DeliveryCost.fromDistanceOnly(BigDecimal.ONE, "XAF"));
        assertThat(d.getStatus()).isEqualTo(DELIVERED);
        assertThat(d.getParcel().getStatus()).isEqualTo(ParcelStatus.UNDER_DISPUTE);
        assertThat(d.getParcel().getPreviousStatusBeforeDispute()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(parcelEvents(d)).containsExactly("ParcelCustodyChanged");
    }

    @Test
    @DisplayName("Creation records DeliveryCreated (D1) and links the parcel; at most one live delivery per parcel")
    void creationAndSingleLiveDelivery() {
        Parcel parcel = Parcel.create(spec());
        Delivery d = Delivery.create(UUID.randomUUID(), null, UUID.randomUUID(), parcel, address(), address(),
                new RecipientInfo("Awa Ndiaye", "+237600000000", null), DeliveryUrgency.STANDARD, null, null);
        assertThat(d.getAggregateEvents()).extracting(AggregateEvent::eventType).containsExactly("DeliveryCreated");
        assertThat(d.getAggregateEvents().getFirst().aggregateVersion()).isZero();
        assertThat(parcel.getCurrentDeliveryId()).isEqualTo(d.getId());
        assertThat(d.getTrackingCode()).isEqualTo(parcel.getTrackingCode());
        assertThatThrownBy(() -> Delivery.create(UUID.randomUUID(), null, UUID.randomUUID(), parcel, address(),
                address(), null, DeliveryUrgency.STANDARD, null, null))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> Delivery.create(UUID.randomUUID(), null, UUID.randomUUID(), Parcel.create(spec()),
                address(), address(), null, DeliveryUrgency.STANDARD, null, null, DeliveryKind.RETURN, null, 3))
                .isInstanceOf(DeliveryDomainException.class);
    }

    @Test
    @DisplayName("Relay storage reminder is raised once (DeliveryAlertRaised), D12 only at the destination hub")
    void relayStorage() {
        Delivery d = deliveryIn(AT_RELAY_HUB, null);
        assertThat(d.raiseStorageReminder(Instant.now())).isTrue();
        assertThat(d.raiseStorageReminder(Instant.now())).isFalse();
        assertThat(d.getAggregateEvents()).extracting(AggregateEvent::eventType)
                .containsExactly("DeliveryAlertRaised");
        Delivery elsewhere = deliveryIn(AT_RELAY_HUB, null);
        elsewhere.setDestinationHub(UUID.randomUUID());
        assertThatThrownBy(elsewhere::expireRelayStorage).isInstanceOf(DeliveryDomainException.class);
        d.expireRelayStorage();
        assertThat(d.getFailureReason()).isEqualTo(DeliveryFailureReason.NOT_COLLECTED_AT_RELAY);
        assertThat(d.getParcel().getReturnCause()).hasToString("NOT_COLLECTED_AT_RELAY");
    }

    // ── fixtures ──────────────────────────────────────────────────────────────────────────────────

    /** A persisted-like delivery in {@code state}, its parcel consistent; {@code memorised} for incident states. */
    static Delivery deliveryIn(DeliveryStatus state, DeliveryStatus memorised) {
        DeliveryStatus operational = state.isIncidentState() ? memorised : state;
        UUID deliveryId = UUID.randomUUID();
        UUID courier = state == CREATED ? null : UUID.randomUUID();
        ParcelStatus parcelState = state.isTerminal() ? ParcelStatus.CREATED
                : DeliveryParcelCorrespondenceTest.consistentParcel(DeliveryKind.OUTBOUND, operational, true)
                .iterator().next();
        Parcel parcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingCode("TNT-20261126-ABCDEFGH")
                .specification(spec())
                .status(parcelState)
                .currentDeliveryId(state.isTerminal() ? null : deliveryId)
                .deliveryIds(new ArrayList<>(List.of(deliveryId)))
                .custody(operational != null && operational.courierHoldsParcel() ? ParcelCustody.courier(courier)
                        : ParcelCustody.sender(null))
                .createdAt(Instant.now())
                .version(1L)
                .build();
        Delivery d = Delivery.builder()
                .id(deliveryId)
                .tenantId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .parcel(parcel)
                .pickupAddress(address())
                .deliveryAddress(address())
                .urgency(DeliveryUrgency.STANDARD)
                .status(state)
                .deliveryPersonId(courier)
                .relayHubId(state == AT_RELAY_HUB ? HUB : null)
                .previousStatusBeforePause(state.isIncidentState() ? memorised : null)
                .pausedByIncidentId(state.isIncidentState() ? UUID.randomUUID() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(4L)
                .build();
        return d;
    }

    private static List<String> parcelEvents(Delivery d) {
        return d.getParcel().getDomainEvents().stream().filter(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).eventType()).toList();
    }

    private static PackageSpecification spec() {
        return new PackageSpecification(2.0, 10, 10, 10, false, false, "Box");
    }

    private static DeliveryAddress address() {
        return new DeliveryAddress("Rue 1", "Near the market", "Bastos", "Yaounde", "CM", null);
    }
}
