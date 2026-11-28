package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.DeliverySnapshot;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryAgreedTermsTest {

    private static final UUID SENDER_ID = UUID.randomUUID();
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        Parcel parcel = Parcel.create(new PackageSpecification(2.5, 30, 20, 15, false, false, "Test"));
        delivery = Delivery.create(UUID.randomUUID(), null, SENDER_ID, parcel,
                new DeliveryAddress("Rue A", null, "D", "Douala", "CM", null),
                new DeliveryAddress("Rue B", null, "D", "Douala", "CM", null),
                new RecipientInfo("Jean", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
        delivery.assignDeliveryPerson(UUID.randomUUID(), null, 5.0, Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("recordAgreedTerms stores the price and the windows without any status change nor event")
    void recordsTerms() {
        Instant now = Instant.now();
        int eventsBefore = delivery.getDomainEvents().size();
        var statusBefore = delivery.getStatus();

        delivery.recordAgreedTerms(new BigDecimal("2500.00"), "XAF",
                now, now.plusSeconds(4 * 3600), now.plusSeconds(3600), now.plusSeconds(24 * 3600));

        assertThat(delivery.getAgreedAmount()).isEqualByComparingTo("2500");
        assertThat(delivery.getAgreedCurrency()).isEqualTo("XAF");
        assertThat(delivery.getPickupWindowStart()).isEqualTo(now);
        assertThat(delivery.getDeliveryWindowEnd()).isEqualTo(now.plusSeconds(24 * 3600));
        assertThat(delivery.getStatus()).isEqualTo(statusBefore);
        assertThat(delivery.getDomainEvents()).hasSize(eventsBefore);
    }

    @Test
    @DisplayName("a null amount keeps the amount already recorded")
    void nullAmountKeepsPrice() {
        delivery.recordAgreedTerms(new BigDecimal("900"), "XAF", null, null, null, null);
        delivery.recordAgreedTerms(null, null, null, null, null, null);

        assertThat(delivery.getAgreedAmount()).isEqualByComparingTo("900");
        assertThat(delivery.getAgreedCurrency()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("a negative amount or a window ending before it starts is refused")
    void refusesIncoherentTerms() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> delivery.recordAgreedTerms(new BigDecimal("-1"), "XAF", null, null, null, null))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> delivery.recordAgreedTerms(null, null, now, now.minusSeconds(1), null, null))
                .isInstanceOf(DeliveryDomainException.class);
    }

    @Test
    @DisplayName("terms cannot be recorded on a terminal delivery")
    void refusedWhenTerminal() {
        delivery.cancel("changed my mind");

        assertThatThrownBy(() -> delivery.recordAgreedTerms(BigDecimal.TEN, "XAF", null, null, null, null))
                .isInstanceOf(DeliveryDomainException.class);
    }

    @Test
    @DisplayName("DeliveryDelivered carries the sender (client) id in its snapshot (lot L8)")
    void completedEventCarriesSender() {
        delivery.confirmPickup();
        delivery.startTransit(EtaEstimate.of(Instant.now().plusSeconds(1800), 5.0, 30));
        delivery.complete(DeliveryCost.ofXaf(BigDecimal.valueOf(1000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        AggregateEvent event = delivery.getAggregateEvents().stream()
                .filter(e -> "DeliveryDelivered".equals(e.eventType()))
                .findFirst().orElseThrow();

        assertThat(((DeliverySnapshot) event.snapshot()).senderId()).isEqualTo(SENDER_ID);
        assertThat(event.aggregateId()).isEqualTo(delivery.getId());
    }
}
