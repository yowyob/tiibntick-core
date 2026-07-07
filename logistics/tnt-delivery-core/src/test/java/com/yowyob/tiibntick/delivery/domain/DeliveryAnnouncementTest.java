package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementPublishedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.AnnouncementResponseSelectedEvent;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponseStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@code DeliveryAnnouncement} aggregate.
 *
 * @author MANFOUO Braun
 */
class DeliveryAnnouncementTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID CLIENT_ID   = UUID.randomUUID();
    private static final UUID DRIVER_1_ID = UUID.randomUUID();
    private static final UUID DRIVER_2_ID = UUID.randomUUID();

    private DeliveryAnnouncement announcement;

    @BeforeEach
    void setUp() {
        Parcel parcel = Parcel.create(new PackageSpecification(
                1.0, 20, 15, 10, false, false, "Documents"));
        announcement = DeliveryAnnouncement.createDraft(
                TENANT_ID, CLIENT_ID, "Livraison urgente Bastos",
                "Colis fragile à livrer rapidement", BigDecimal.valueOf(3000), "XAF",
                parcel, pickupAddr(), deliveryAddr(),
                new RecipientInfo("Marie Ngono", "+237691000002", null),
                DeliveryUrgency.EXPRESS);
    }

    @Test
    @DisplayName("Draft announcement should be in DRAFT status")
    void shouldCreateDraft() {
        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.DRAFT);
        assertThat(announcement.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Publishing a DRAFT should move to PUBLISHED and emit event")
    void shouldPublishDraft() {
        announcement.publish();

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(announcement.getDomainEvents())
                .anyMatch(e -> e instanceof AnnouncementPublishedEvent);
    }

    @Test
    @DisplayName("Adding a response to PUBLISHED should transition to IN_NEGOTIATION")
    void shouldTransitionToNegotiationOnFirstResponse() {
        announcement.publish();
        announcement.clearDomainEvents();

        AnnouncementResponse response = AnnouncementResponse.create(
                announcement.getId(), DRIVER_1_ID, Instant.now().plusSeconds(600), "Je suis disponible");
        announcement.addResponse(response);

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.IN_NEGOTIATION);
        assertThat(announcement.getResponses()).hasSize(1);
    }

    @Test
    @DisplayName("Selecting a response should assign winner and reject others")
    void shouldSelectResponseAndRejectOthers() {
        announcement.publish();
        AnnouncementResponse resp1 = AnnouncementResponse.create(
                announcement.getId(), DRIVER_1_ID, Instant.now().plusSeconds(300), "");
        AnnouncementResponse resp2 = AnnouncementResponse.create(
                announcement.getId(), DRIVER_2_ID, Instant.now().plusSeconds(600), "");
        announcement.addResponse(resp1);
        announcement.addResponse(resp2);
        announcement.clearDomainEvents();

        announcement.selectResponse(resp1.getId());

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.ASSIGNED);
        assertThat(announcement.getSelectedResponseId()).isEqualTo(resp1.getId());
        assertThat(resp1.getStatus()).isEqualTo(ResponseStatus.ACCEPTED);
        assertThat(resp2.getStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(announcement.getDomainEvents())
                .anyMatch(e -> e instanceof AnnouncementResponseSelectedEvent);
    }

    @Test
    @DisplayName("Cancelling a PUBLISHED announcement should set status CANCELLED")
    void shouldCancelPublishedAnnouncement() {
        announcement.publish();
        announcement.cancel();

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.CANCELLED);
    }

    @Test
    @DisplayName("Publishing already PUBLISHED announcement should throw")
    void shouldThrowWhenPublishingNonDraft() {
        announcement.publish();

        assertThatThrownBy(announcement::publish)
                .isInstanceOf(DeliveryDomainException.class)
                .hasMessageContaining("DRAFT");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static DeliveryAddress pickupAddr() {
        return new DeliveryAddress(null, "Marché Central", "Centre-ville",
                "Yaoundé", "CM", new GeoCoordinates(3.8650, 11.5180));
    }

    private static DeliveryAddress deliveryAddr() {
        return new DeliveryAddress(null, "Ambassade Bastos", "Bastos",
                "Yaoundé", "CM", new GeoCoordinates(3.8810, 11.5270));
    }
}
