package com.yowyob.tiibntick.delivery.domain.statemachine;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ParcelStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.AnnouncementStateMachine;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.ParcelStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parcel dispute rows (P12–P17) from every parcel state, and the announcement / response rows (A1–A7, R1–R4) from
 * every announcement state, through the aggregates.
 */
class ParcelAndAnnouncementAggregatesTest {

    private static final TntEventActor SUPPORT = TntEventActor.of(TntEventActor.Type.SUPPORT, null);

    @ParameterizedTest(name = "openDispute from {0}")
    @EnumSource(ParcelStatus.class)
    @DisplayName("P12: a dispute opens from every state but UNDER_DISPUTE/LOST/DAMAGED; closed states need the window")
    void openDisputeFromEveryState(ParcelStatus from) {
        boolean allowed = ParcelStateMachine.TABLE.isAllowed(from, ParcelStatus.UNDER_DISPUTE);
        Parcel parcel = parcelIn(from);
        if (from == ParcelStatus.UNDER_DISPUTE) {
            parcel = parcelIn(ParcelStatus.IN_TRANSIT);
            parcel.openDispute(UUID.randomUUID(), true, true, SUPPORT, null, null);
            parcel.clearDomainEvents();
        }
        Parcel p = parcel;
        if (!allowed) {
            assertThatThrownBy(() -> p.openDispute(UUID.randomUUID(), true, true, SUPPORT, null, null))
                    .isInstanceOf(DeliveryDomainException.class);
            assertThat(eventTypes(p)).isEmpty();
            return;
        }
        if (from.isClosed()) {
            assertThatThrownBy(() -> p.openDispute(UUID.randomUUID(), true, false, SUPPORT, null, null))
                    .isInstanceOf(DeliveryDomainException.class);
        }
        p.openDispute(UUID.randomUUID(), true, true, SUPPORT, null, null);
        assertThat(p.getStatus()).isEqualTo(ParcelStatus.UNDER_DISPUTE);
        assertThat(p.getPreviousStatusBeforeDispute()).isEqualTo(from);
        assertThat(eventTypes(p)).containsExactly("ParcelUnderDispute");
    }

    @ParameterizedTest(name = "outcome {0}")
    @EnumSource(DisputeParcelOutcome.class)
    @DisplayName("P13-P17: each outcome leads to its state with one event; refused without an open dispute")
    void disputeOutcomes(DisputeParcelOutcome outcome) {
        Parcel parcel = parcelIn(ParcelStatus.DELIVERED);
        assertThatThrownBy(() -> parcel.applyDisputeOutcome(outcome, "DISPUTE_X", null, SUPPORT, null, null))
                .isInstanceOf(DeliveryDomainException.class);
        parcel.openDispute(UUID.randomUUID(), true, true, SUPPORT, null, null);
        parcel.clearDomainEvents();
        parcel.applyDisputeOutcome(outcome, "DISPUTE_X", "1500.00", SUPPORT, null, null);
        ParcelStatus expected = switch (outcome) {
            case RESTORE_PREVIOUS -> ParcelStatus.DELIVERED;
            case LOST -> ParcelStatus.LOST;
            case DAMAGED -> ParcelStatus.DAMAGED;
            case RETURN_TO_SENDER -> ParcelStatus.RETURN_REQUESTED;
            case REDELIVERY -> ParcelStatus.CREATED;
        };
        assertThat(parcel.getStatus()).isEqualTo(expected);
        assertThat(parcel.getOpenDisputeId()).isNull();
        assertThat(eventTypes(parcel)).hasSize(1).first().isEqualTo("Parcel" + pascal(expected.name()));
    }

    @Test
    @DisplayName("P1 assigns an immutable tracking code; P19 counts failed returns")
    void creationAndTrackingCode() {
        Parcel parcel = Parcel.create(spec(), UUID.randomUUID(), UUID.randomUUID(), TntEventActor.system());
        assertThat(parcel.getTrackingCode()).matches("TNT-\\d{8}-[A-Z0-9]{8}");
        assertThat(parcel.ensureTrackingCode()).isFalse();
        assertThat(eventTypes(parcel)).containsExactly("ParcelCreated");
        Parcel legacy = Parcel.builder().id(UUID.randomUUID()).specification(spec()).createdAt(Instant.now())
                .version(3L).build();
        assertThat(legacy.hasPendingChanges()).isFalse();
        assertThat(legacy.ensureTrackingCode()).isTrue();
        assertThat(legacy.hasPendingChanges()).isTrue();
    }

    // ── announcement ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A1..A4, A7 and R1..R3 along the nominal path, one event per transition")
    void nominalAnnouncement() {
        DeliveryAnnouncement a = draft();
        assertThat(eventTypes(a)).containsExactly("AnnouncementDraft");
        a.clearDomainEvents();
        a.publish();
        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(eventTypes(a)).containsExactly("AnnouncementPublished");
        a.clearDomainEvents();
        AnnouncementResponse r1 = response(a);
        AnnouncementResponse r2 = response(a);
        a.addResponse(r1);
        a.addResponse(r2);
        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.IN_NEGOTIATION);
        assertThat(eventTypes(a)).containsExactly("AnnouncementInNegotiation", "AnnouncementResponseReceived");
        a.clearDomainEvents();
        a.selectResponse(r1.getId());
        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.ASSIGNED);
        assertThat(r1.getStatus()).isEqualTo(ResponseStatus.ACCEPTED);
        assertThat(r2.getStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(eventTypes(a)).containsExactly("AnnouncementAssigned");
        assertThatThrownBy(a::cancel).isInstanceOf(DeliveryDomainException.class);
        a.clearDomainEvents();
        a.complete();
        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.COMPLETED);
        assertThat(eventTypes(a)).containsExactly("AnnouncementCompleted");
    }

    @ParameterizedTest(name = "from {0}")
    @EnumSource(AnnouncementStatus.class)
    @DisplayName("Every announcement operation from every state follows the table (6 states x 5 operations)")
    void announcementProduct(AnnouncementStatus from) {
        check(from, AnnouncementStatus.PUBLISHED, DeliveryAnnouncement::publish);
        check(from, AnnouncementStatus.CANCELLED, DeliveryAnnouncement::cancel);
        check(from, AnnouncementStatus.CANCELLED, DeliveryAnnouncement::cancelWithDelivery);
        check(from, AnnouncementStatus.COMPLETED, DeliveryAnnouncement::complete);
        check(from, AnnouncementStatus.ASSIGNED, a -> {
            AnnouncementResponse r = a.getResponses().isEmpty() ? null : a.getResponses().getFirst();
            a.selectResponse(r == null ? UUID.randomUUID() : r.getId());
        });
    }

    @Test
    @DisplayName("R2-R4 are refused from a final response state")
    void responseRows() {
        DeliveryAnnouncement a = draft();
        a.publish();
        AnnouncementResponse r = response(a);
        a.addResponse(r);
        r.cancel();
        assertThat(r.getStatus()).isEqualTo(ResponseStatus.CANCELLED);
        assertThatThrownBy(r::accept).isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(r::reject).isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(r::cancel).isInstanceOf(DeliveryDomainException.class);
        assertThat(AnnouncementStateMachine.RESPONSE_TABLE.isAllowed(ResponseStatus.CANCELLED,
                ResponseStatus.ACCEPTED)).isFalse();
    }

    private void check(AnnouncementStatus from, AnnouncementStatus to,
                       java.util.function.Consumer<DeliveryAnnouncement> op) {
        DeliveryAnnouncement a = announcementIn(from);
        a.clearDomainEvents();
        boolean allowed = AnnouncementStateMachine.TABLE.isAllowed(from, to);
        try {
            op.accept(a);
        } catch (DeliveryDomainException refused) {
            assertThat(a.getStatus()).isEqualTo(from);
            if (allowed) {
                // allowed pair refused for another reason: the other row of the target (A5 vs A6), or A4 from
                // PUBLISHED without any response (the first response always moves PUBLISHED to IN_NEGOTIATION)
                assertThat(to == AnnouncementStatus.CANCELLED
                        || to == AnnouncementStatus.ASSIGNED && a.getResponses().isEmpty())
                        .as("%s -> %s refused: %s", from, to, refused.getMessage()).isTrue();
            }
            return;
        }
        assertThat(allowed).as("%s -> %s accepted", from, to).isTrue();
        assertThat(a.getStatus()).isEqualTo(to);
        assertThat(eventTypes(a)).last().isEqualTo("Announcement" + pascal(to.name()));
    }

    private DeliveryAnnouncement announcementIn(AnnouncementStatus state) {
        DeliveryAnnouncement a = draft();
        switch (state) {
            case DRAFT -> { }
            case PUBLISHED -> a.publish();
            case IN_NEGOTIATION -> {
                a.publish();
                a.addResponse(response(a));
            }
            case ASSIGNED -> {
                a.publish();
                AnnouncementResponse r = response(a);
                a.addResponse(r);
                a.selectResponse(r.getId());
            }
            case COMPLETED -> {
                a.publish();
                AnnouncementResponse r = response(a);
                a.addResponse(r);
                a.selectResponse(r.getId());
                a.complete();
            }
            case CANCELLED -> a.cancel();
        }
        return a;
    }

    private static DeliveryAnnouncement draft() {
        DeliveryAddress address = new DeliveryAddress("Rue 1", null, "Bastos", "Yaounde", "CM",
                new GeoCoordinates(3.87, 11.51));
        return DeliveryAnnouncement.createDraft(UUID.randomUUID(), UUID.randomUUID(), "Box", "A box",
                BigDecimal.valueOf(2000), "XAF", Parcel.create(spec()), address, address,
                new RecipientInfo("Awa Ndiaye", "+237600000000", null), DeliveryUrgency.STANDARD);
    }

    private static AnnouncementResponse response(DeliveryAnnouncement a) {
        return AnnouncementResponse.create(a.getId(), UUID.randomUUID(), Instant.now().plusSeconds(3600), "ok");
    }

    private static Parcel parcelIn(ParcelStatus state) {
        return Parcel.builder().id(UUID.randomUUID()).trackingCode("TNT-20261126-ZZZZZZZZ").specification(spec())
                .status(state).createdAt(Instant.now()).version(1L).build();
    }

    private static PackageSpecification spec() {
        return new PackageSpecification(1.0, 10, 10, 10, false, false, "Box");
    }

    private static List<String> eventTypes(com.yowyob.tiibntick.core.delivery.domain.event.EventSource source) {
        return source.getDomainEvents().stream().filter(AggregateEvent.class::isInstance)
                .map(e -> ((AggregateEvent) e).eventType()).toList();
    }

    private static String pascal(String upperSnake) {
        StringBuilder sb = new StringBuilder();
        for (String w : upperSnake.split("_")) {
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
