package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.AnnouncementPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Optional sender / recipient e-mail / generic details of an announcement: aggregate and persistence round trip. */
@DisplayName("DeliveryAnnouncement : complements optionnels")
class DeliveryAnnouncementDetailsTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID HUB = UUID.randomUUID();
    private static final UUID NEED = UUID.randomUUID();

    private DeliveryAnnouncement announcement;

    private static DeliveryAddress addr(String city) {
        return new DeliveryAddress("Rue 1", "Carrefour", "Bastos", city, "CM", null);
    }

    @BeforeEach
    void setUp() {
        Parcel parcel = Parcel.create(new PackageSpecification(2.0, 20, 15, 10, true, false, "Colis"));
        announcement = DeliveryAnnouncement.createDraft(
                TENANT, CLIENT, "Titre", "desc", new BigDecimal("2500"), "XAF",
                AnnouncementPricingMode.FIXED_PRICE, parcel, addr("Yaounde"), addr("Douala"),
                RecipientInfo.of("Marie Ngono", "+237691000002", "marie@example.org"),
                DeliveryUrgency.SAME_DAY,
                new SenderInfo("Jean Mbarga", "+237690000001", "jean@example.org"),
                new AnnouncementDetails("MOTORBIKE", "CASH", "ROAD", new BigDecimal("12.500"), 45, HUB,
                        "sig://abc", new BigDecimal("300.00"), "DELIVERY_NEED", NEED));
        announcement.publish();
    }

    @Test
    @DisplayName("les complements survivent a l'aller-retour entite")
    void persistenceRoundTrip() {
        DeliveryAnnouncementEntity entity = AnnouncementPersistenceMapper.toEntity(announcement);
        assertThat(entity.getSenderName()).isEqualTo("Jean Mbarga");
        assertThat(entity.getRecipientEmail()).isEqualTo("marie@example.org");
        assertThat(entity.getOriginRefType()).isEqualTo("DELIVERY_NEED");

        DeliveryAnnouncement back = AnnouncementPersistenceMapper.toDomain(entity, announcement.getParcel(), List.of());
        assertThat(back.getSender()).isEqualTo(announcement.getSender());
        assertThat(back.getRecipient().email()).isEqualTo("marie@example.org");
        assertThat(back.getDetails()).isEqualTo(announcement.getDetails());
        assertThat(back.getUrgency()).isEqualTo(DeliveryUrgency.SAME_DAY);
    }

    @Test
    @DisplayName("une annonce sans complement relit des valeurs vides, jamais null")
    void legacyRowReadsEmptyDetails() {
        DeliveryAnnouncementEntity entity = AnnouncementPersistenceMapper.toEntity(announcement);
        entity.setSenderName(null); entity.setSenderPhone(null); entity.setSenderEmail(null);
        entity.setPaymentMethod(null); entity.setOriginRefId(null); entity.setOriginRefType(null);
        DeliveryAnnouncement back = AnnouncementPersistenceMapper.toDomain(entity, announcement.getParcel(), List.of());
        assertThat(back.getSender()).isNull();
        assertThat(back.getDetails()).isNotNull();
        assertThat(back.getDetails().paymentMethod()).isNull();
    }

    @Test
    @DisplayName("updateDetails fusionne les composantes non nulles et garde l'origine")
    void updateDetailsMerges() {
        announcement.updateDetails(null, new AnnouncementDetails(null, "MOBILE_MONEY", null, null, null, null,
                null, null, "OTHER", UUID.randomUUID()));
        assertThat(announcement.getDetails().paymentMethod()).isEqualTo("MOBILE_MONEY");
        assertThat(announcement.getDetails().requiredVehicleType()).isEqualTo("MOTORBIKE");
        assertThat(announcement.getDetails().originRefType()).isEqualTo("DELIVERY_NEED");
        assertThat(announcement.getDetails().originRefId()).isEqualTo(NEED);
        assertThat(announcement.getSender().name()).isEqualTo("Jean Mbarga");
    }

    @Test
    @DisplayName("updateTerms refuse une annonce deja attribuee")
    void updateTermsRefusedWhenAssigned() {
        AnnouncementResponse r = AnnouncementResponse.create(announcement.getId(), UUID.randomUUID(),
                Instant.now().plusSeconds(600), "ok");
        announcement.addResponse(r);
        announcement.updateTerms(new BigDecimal("3000"), "XAF");
        assertThat(announcement.getOfferedAmount()).isEqualByComparingTo("3000");
        announcement.selectResponse(r.getId());
        assertThatThrownBy(() -> announcement.updateTerms(new BigDecimal("1"), null))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> announcement.updateTerms(new BigDecimal("-1"), null))
                .isInstanceOf(DeliveryDomainException.class);
    }

    @Test
    @DisplayName("le constructeur a 3 arguments de RecipientInfo reste valable (pas d'email)")
    void recipientBackwardCompatible() {
        assertThat(new RecipientInfo("A", "+2376", null).email()).isNull();
        assertThat(SenderInfo.class.getRecordComponents()).hasSize(3);
    }
}
