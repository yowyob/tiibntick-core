package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.event.*;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.*;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@code Delivery} aggregate state machine.
 * Tests all valid and invalid transitions, and domain event collection.
 *
 * @author MANFOUO Braun
 */
class DeliveryStateMachineTest {

    private static final UUID TENANT_ID          = UUID.randomUUID();
    private static final UUID SENDER_ID          = UUID.randomUUID();
    //private static final UUID DELIVERY_PERSON_ID = UUID.randomUUID();

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        Parcel parcel = Parcel.create(new PackageSpecification(
                2.5, 30, 20, 15, false, false, "Test parcel"));

        delivery = Delivery.create(
                TENANT_ID, null, SENDER_ID, parcel,
                pickupAddress(), deliveryAddress(),
                new RecipientInfo("Jean Dupont", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
    }

    @Test
    @DisplayName("Initial status should be CREATED with DeliveryCreatedEvent")
    void shouldStartWithCreatedStatus() {
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(delivery.getDomainEvents()).hasSize(1);
        assertThat(delivery.getDomainEvents().get(0)).isInstanceOf(DeliveryCreatedEvent.class);
    }

    @Test
    @DisplayName("CREATED → PICKED_UP should succeed")
    void shouldTransitionCreatedToPickedUp() {
        delivery.confirmPickup();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(delivery.getActualPickupTime()).isNotNull();
        assertThat(delivery.getDomainEvents())
                .anyMatch(e -> e instanceof ParcelPickedUpEvent);
    }

    @Test
    @DisplayName("PICKED_UP → IN_TRANSIT should succeed and set ETA")
    void shouldTransitionToInTransit() {
        delivery.confirmPickup();
        delivery.clearDomainEvents();

        EtaEstimate eta = EtaEstimate.of(Instant.now().plusSeconds(1800), 5.2, 30);
        delivery.startTransit(eta);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(delivery.getCurrentEta()).isNotNull();
        assertThat(delivery.getDomainEvents())
                .anyMatch(e -> e instanceof DeliveryInTransitEvent);
    }

    @Test
    @DisplayName("IN_TRANSIT → DELIVERED should succeed with final cost")
    void shouldCompleteDelivery() {
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        delivery.clearDomainEvents();

        DeliveryCost finalCost = DeliveryCost.fromDistanceOnly(BigDecimal.valueOf(2500), "XAF");
        delivery.complete(finalCost);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getFinalCost()).isNotNull();
        assertThat(delivery.getActualDeliveryTime()).isNotNull();
        assertThat(delivery.getDomainEvents())
                .anyMatch(e -> e instanceof DeliveryCompletedEvent);
    }

    @Test
    @DisplayName("IN_TRANSIT → AT_RELAY_POINT → IN_TRANSIT should succeed")
    void shouldSupportRelayPointStop() {
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(3600), 10.0, 60));
        delivery.clearDomainEvents();

        UUID relayId = UUID.randomUUID();
        delivery.depositAtRelayPoint(relayId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.AT_RELAY_POINT);
        assertThat(delivery.getDomainEvents()).anyMatch(e -> e instanceof ParcelAtRelayPointEvent);

        delivery.resumeFromRelayPoint(EtaEstimate.of(Instant.now().plusSeconds(1800), 5.0, 30));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("IN_TRANSIT → FAILED should succeed with reason")
    void shouldFailDelivery() {
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        delivery.clearDomainEvents();

        delivery.fail("Address not found");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getNotes()).contains("FAILURE: Address not found");
        assertThat(delivery.getDomainEvents()).anyMatch(e -> e instanceof DeliveryFailedEvent);
    }

    @Test
    @DisplayName("CREATED → CANCELLED should succeed")
    void shouldCancelFromCreated() {
        delivery.cancel("Changed mind");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(delivery.getDomainEvents()).anyMatch(e -> e instanceof DeliveryCancelledEvent);
    }

    @Test
    @DisplayName("DELIVERED → PICKED_UP should throw InvalidDeliveryStateTransitionException")
    void shouldRejectInvalidTransitionFromTerminalState() {
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(900), 3.0, 15));
        delivery.complete(DeliveryCost.fromDistanceOnly(BigDecimal.valueOf(2000), "XAF"));

        assertThatThrownBy(delivery::confirmPickup)
                .isInstanceOf(InvalidDeliveryStateTransitionException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("clearDomainEvents should empty the events list")
    void shouldClearDomainEvents() {
        assertThat(delivery.getDomainEvents()).isNotEmpty();
        delivery.clearDomainEvents();
        assertThat(delivery.getDomainEvents()).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static DeliveryAddress pickupAddress() {
        return new DeliveryAddress(null, "Marché Mokolo", "Mokolo",
                "Yaoundé", "CM", new GeoCoordinates(3.8700, 11.5160));
    }

    private static DeliveryAddress deliveryAddress() {
        return new DeliveryAddress(null, "Carrefour Bastos", "Bastos",
                "Yaoundé", "CM", new GeoCoordinates(3.8800, 11.5250));
    }
}
