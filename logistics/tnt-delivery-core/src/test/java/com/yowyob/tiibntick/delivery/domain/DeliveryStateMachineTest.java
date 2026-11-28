package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.event.*;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Nominal paths of the {@code Delivery} aggregate (lot evt-l2 rows D1–D12): each step records its
 * {@code Delivery<Target>} event, the parcel follows, and the legacy events are still recorded for the old topics
 * (TODO(L8)). The full product states × states is in {@code statemachine.DeliveryAggregateTransitionsTest}.
 *
 * @author MANFOUO Braun
 */
class DeliveryStateMachineTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();

    private Delivery delivery;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcel = Parcel.create(new PackageSpecification(2.5, 30, 20, 15, false, false, "Test parcel"));
        delivery = Delivery.create(
                TENANT_ID, null, SENDER_ID, parcel,
                pickupAddress(), deliveryAddress(),
                new RecipientInfo("Jean Dupont", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
    }

    @Test
    @DisplayName("D1: CREATED with DeliveryCreated only (lot L8: no legacy event), parcel CREATED")
    void shouldStartWithCreatedStatus() {
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType)
                .containsExactly("DeliveryCreated");
        assertThat(delivery.getDomainEvents()).allMatch(AggregateEvent.class::isInstance);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    @DisplayName("D-136: the tracking code is the parcel's")
    void createAssignsValidTrackingCode() {
        assertThat(delivery.getTrackingCode()).matches("TNT-\\d{8}-[A-Z0-9]{8}")
                .isEqualTo(parcel.getTrackingCode());
    }

    @Test
    @DisplayName("lot L8: a transition records only the aggregate event, its snapshot carrying the new status")
    void transitionRecordsOnlyTheAggregateEvent() {
        delivery.clearDomainEvents();
        assign();
        assertThat(delivery.getDomainEvents()).allMatch(AggregateEvent.class::isInstance);
        AggregateEvent event = delivery.getAggregateEvents().get(0);
        assertThat(event.eventType()).isEqualTo("DeliveryAssigned");
        assertThat(event.previousStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("CREATED → PICKED_UP is refused: a courier must be assigned first (D2 then D4)")
    void pickupNeedsAssignment() {
        assertThatThrownBy(delivery::confirmPickup).isInstanceOf(InvalidDeliveryStateTransitionException.class);
        assign();
        delivery.confirmPickup();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(delivery.getActualPickupTime()).isNotNull();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(parcel.getCustody()).isEqualTo(ParcelCustody.courier(COURIER_ID));
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryPickedUp");
    }

    @Test
    @DisplayName("PICKED_UP → IN_TRANSIT sets the ETA")
    void shouldTransitionToInTransit() {
        assign();
        delivery.confirmPickup();
        delivery.clearDomainEvents();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(1800), 5.2, 30));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(delivery.getCurrentEta()).isNotNull();
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType)
                .containsExactly("DeliveryInTransit");
    }

    @Test
    @DisplayName("IN_TRANSIT → DELIVERED with final cost; the parcel is DELIVERED and released")
    void shouldCompleteDelivery() {
        toInTransit();
        delivery.complete(DeliveryCost.fromDistanceOnly(BigDecimal.valueOf(2500), "XAF"));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getFinalCost()).isNotNull();
        assertThat(delivery.getActualDeliveryTime()).isNotNull();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(parcel.getCurrentDeliveryId()).isNull();
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryDelivered");
    }

    @Test
    @DisplayName("IN_TRANSIT → AT_RELAY_HUB → IN_TRANSIT (D7, D6) moves the parcel custody")
    void shouldSupportRelayHubStop() {
        toInTransit();
        UUID relayId = UUID.randomUUID();
        delivery.depositAtRelayHub(RelayDeposit.at(relayId));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.AT_RELAY_HUB);
        assertThat(parcel.getCustody().holderType().name()).isEqualTo("RELAY");
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryAtRelayHub");

        delivery.resumeFromRelayHub(EtaEstimate.of(Instant.now().plusSeconds(1800), 5.0, 30));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(delivery.getRelayHubId()).isNull();
        assertThat(parcel.getCustody()).isEqualTo(ParcelCustody.courier(COURIER_ID));
    }

    @Test
    @DisplayName("A refusal by the recipient fails the delivery at once (D10) and the parcel goes back (P11)")
    void shouldFailDeliveryOnRefusal() {
        toInTransit();
        delivery.recordFailedAttempt(DeliveryFailureReason.CLIENT_REFUSED_RECEPTION, 3);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getFailureReason()).isEqualTo(DeliveryFailureReason.CLIENT_REFUSED_RECEPTION);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.RETURN_REQUESTED);
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryFailed");
    }

    @Test
    @DisplayName("CREATED → CANCELLED with a reason")
    void shouldCancelFromCreated() {
        delivery.cancel("Changed mind");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(delivery.getCancellationReason()).isEqualTo("Changed mind");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(delivery.getAggregateEvents()).extracting(AggregateEvent::eventType).contains("DeliveryCancelled");
    }

    @Test
    @DisplayName("DELIVERED → PICKED_UP is refused")
    void shouldRejectInvalidTransitionFromTerminalState() {
        toInTransit();
        delivery.complete(DeliveryCost.fromDistanceOnly(BigDecimal.valueOf(2000), "XAF"));
        assertThatThrownBy(delivery::confirmPickup)
                .isInstanceOf(InvalidDeliveryStateTransitionException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("clearDomainEvents empties the delivery and parcel events")
    void shouldClearDomainEvents() {
        assertThat(delivery.getDomainEvents()).isNotEmpty();
        delivery.clearDomainEvents();
        assertThat(delivery.getDomainEvents()).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void assign() {
        delivery.assignDeliveryPerson(COURIER_ID, null, 3.0, null);
    }

    private void toInTransit() {
        assign();
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        delivery.clearDomainEvents();
    }

    private static DeliveryAddress pickupAddress() {
        return new DeliveryAddress(null, "Marché Mokolo", "Mokolo",
                "Yaoundé", "CM", new GeoCoordinates(3.8700, 11.5160));
    }

    private static DeliveryAddress deliveryAddress() {
        return new DeliveryAddress(null, "Carrefour Bastos", "Bastos",
                "Yaoundé", "CM", new GeoCoordinates(3.8800, 11.5250));
    }
}
