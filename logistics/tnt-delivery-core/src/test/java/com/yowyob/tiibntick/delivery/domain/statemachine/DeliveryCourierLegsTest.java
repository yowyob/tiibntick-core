package com.yowyob.tiibntick.delivery.domain.statemachine;

import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.ParcelCustody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.ASSIGNED;
import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.INCIDENT_HANDOVER;
import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.IN_TRANSIT;
import static com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus.PAUSED_BY_INCIDENT;
import static org.assertj.core.api.Assertions.assertThat;

/** QO-11 / D-153: a change of courier publishes the courier legs that pay the replacement at the prorata. */
class DeliveryCourierLegsTest {

    // Yaoundé: pickup at Bastos, delivery at Mvan, hand-over at the Poste centrale
    private static final GeoCoordinates PICKUP = new GeoCoordinates(3.8840, 11.5130);
    private static final GeoCoordinates DESTINATION = new GeoCoordinates(3.8160, 11.5200);
    private static final GeoCoordinates HANDOVER = new GeoCoordinates(3.8667, 11.5167);
    private static final GeoCoordinates SECOND_HANDOVER = new GeoCoordinates(3.8400, 11.5180);

    @Test
    @DisplayName("D18 with the hand-over point: leg of the courier who leaves, remaining distance, new leg start")
    void handoverPublishesTheLegs() {
        Delivery d = deliveryIn(INCIDENT_HANDOVER, IN_TRANSIT);
        UUID original = d.getDeliveryPersonId();
        UUID replacement = UUID.randomUUID();

        d.resumeAfterIncident(null, replacement, null, HANDOVER);

        Map<String, Object> details = lastEvent(d).details();
        assertThat(details).containsEntry("replacedCourierId", original.toString())
                .containsEntry("replacementCourierId", replacement.toString());
        assertThat((BigDecimal) details.get("replacedLegKm"))
                .isEqualByComparingTo(km(PICKUP.haversineDistanceTo(HANDOVER)));
        assertThat((BigDecimal) details.get("remainingKm"))
                .isEqualByComparingTo(km(HANDOVER.haversineDistanceTo(DESTINATION)));
        assertThat(d.getLegStartLatitude()).isEqualTo(HANDOVER.latitude());
        assertThat(d.getLegStartLongitude()).isEqualTo(HANDOVER.longitude());
    }

    @Test
    @DisplayName("Second hand-over: the leg starts at the previous hand-over point, not at the pickup")
    void secondHandoverStartsAtThePreviousPoint() {
        Delivery d = deliveryIn(INCIDENT_HANDOVER, IN_TRANSIT);
        d.resumeAfterIncident(null, UUID.randomUUID(), null, HANDOVER);
        UUID second = d.getDeliveryPersonId();
        // a second incident and hand-over on the same delivery
        Delivery again = rebuild(d, INCIDENT_HANDOVER, IN_TRANSIT);
        UUID third = UUID.randomUUID();

        again.resumeAfterIncident(null, third, null, SECOND_HANDOVER);

        Map<String, Object> details = lastEvent(again).details();
        assertThat(details).containsEntry("replacedCourierId", second.toString());
        assertThat((BigDecimal) details.get("replacedLegKm"))
                .isEqualByComparingTo(km(HANDOVER.haversineDistanceTo(SECOND_HANDOVER)));
        assertThat((BigDecimal) details.get("remainingKm"))
                .isEqualByComparingTo(km(SECOND_HANDOVER.haversineDistanceTo(DESTINATION)));
    }

    @Test
    @DisplayName("D18 without a known point: the distances are null (the administrator splits)")
    void unknownPointGivesNullDistances() {
        Delivery d = deliveryIn(INCIDENT_HANDOVER, IN_TRANSIT);
        d.resumeAfterIncident(null, UUID.randomUUID(), null);
        Map<String, Object> details = lastEvent(d).details();
        assertThat(details).containsKey("replacementCourierId").containsEntry("replacedLegKm", null)
                .containsEntry("remainingKm", null);
        assertThat(d.getLegStartLatitude()).isNull();
    }

    @Test
    @DisplayName("Replaced before the pickup (D16): the courier who leaves covered 0 km")
    void replacementBeforePickup() {
        Delivery d = deliveryIn(PAUSED_BY_INCIDENT, ASSIGNED);
        UUID original = d.getDeliveryPersonId();
        UUID replacement = UUID.randomUUID();

        d.resumeAfterIncident("INCIDENT_RESOLVED", replacement, null);

        Map<String, Object> details = lastEvent(d).details();
        assertThat(details).containsEntry("replacedCourierId", original.toString())
                .containsEntry("replacementCourierId", replacement.toString());
        assertThat((BigDecimal) details.get("replacedLegKm")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) details.get("remainingKm"))
                .isEqualByComparingTo(km(PICKUP.haversineDistanceTo(DESTINATION)));
    }

    @Test
    @DisplayName("Same courier resumes: no courier legs in the details")
    void sameCourierHasNoLegs() {
        Delivery d = deliveryIn(PAUSED_BY_INCIDENT, IN_TRANSIT);
        d.resumeAfterIncident("INCIDENT_RESOLVED", null, null);
        assertThat(lastEvent(d).details()).doesNotContainKey("replacementCourierId");
    }

    private static BigDecimal km(double v) {
        return BigDecimal.valueOf(v).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private static AggregateEvent lastEvent(Delivery d) {
        return d.getAggregateEvents().getLast();
    }

    private static Delivery deliveryIn(DeliveryStatus state, DeliveryStatus memorised) {
        UUID deliveryId = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        return build(deliveryId, courier, state, memorised, null, null);
    }

    /** The same delivery, interrupted again by a second incident (the leg start is kept, as when it is reloaded). */
    private static Delivery rebuild(Delivery d, DeliveryStatus state, DeliveryStatus memorised) {
        return build(d.getId(), d.getDeliveryPersonId(), state, memorised, d.getLegStartLatitude(),
                d.getLegStartLongitude());
    }

    private static Delivery build(UUID deliveryId, UUID courier, DeliveryStatus state, DeliveryStatus memorised,
                                  Double legLat, Double legLng) {
        ParcelStatus parcelState = DeliveryParcelCorrespondenceTest
                .consistentParcel(DeliveryKind.OUTBOUND, memorised, true).iterator().next();
        Parcel parcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingCode("TNT-20261127-ABCDEFGH")
                .specification(new PackageSpecification(2.0, 10, 10, 10, false, false, "Box"))
                .status(parcelState)
                .currentDeliveryId(deliveryId)
                .deliveryIds(new ArrayList<>(List.of(deliveryId)))
                .custody(memorised.courierHoldsParcel() ? ParcelCustody.courier(courier) : ParcelCustody.sender(null))
                .createdAt(Instant.now())
                .version(1L)
                .build();
        return Delivery.builder()
                .id(deliveryId)
                .tenantId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .parcel(parcel)
                .pickupAddress(new DeliveryAddress("Rue 1", "Carrefour", "Bastos", "Yaounde", "CM", PICKUP))
                .deliveryAddress(new DeliveryAddress("Rue 2", "Marché", "Mvan", "Yaounde", "CM", DESTINATION))
                .urgency(DeliveryUrgency.STANDARD)
                .status(state)
                .deliveryPersonId(courier)
                .previousStatusBeforePause(memorised)
                .pausedByIncidentId(UUID.randomUUID())
                .legStartLatitude(legLat)
                .legStartLongitude(legLng)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(4L)
                .build();
    }
}
