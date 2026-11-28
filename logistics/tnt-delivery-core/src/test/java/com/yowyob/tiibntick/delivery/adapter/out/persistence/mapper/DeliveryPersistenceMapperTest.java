package com.yowyob.tiibntick.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.DeliveryPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for {@code DeliveryPersistenceMapper}, covering the tracking code
 * field that previously had no assignment path from {@code Delivery.create()} through
 * to the {@code tracking_code} DB column and back.
 *
 * @author MANFOUO Braun
 */
class DeliveryPersistenceMapperTest {

    @Test
    @DisplayName("D-136: the tracking code of a delivery is its parcel's; legacy delivery codes still resolve")
    void trackingCodeSurvivesRoundTrip() {
        Parcel parcel = Parcel.create(new PackageSpecification(
                2.5, 30, 20, 15, false, false, "Test parcel"));

        Delivery delivery = Delivery.create(
                UUID.randomUUID(), null, UUID.randomUUID(), parcel,
                new DeliveryAddress("Rue A", null, "District", "Douala", "CM", null),
                new DeliveryAddress("Rue B", null, "District", "Douala", "CM", null),
                new RecipientInfo("Jean Dupont", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);

        assertThat(delivery.getTrackingCode()).isNotBlank();

        // D-136: the code is the parcel's; a new delivery row carries no code of its own
        assertThat(delivery.getTrackingCode()).isEqualTo(parcel.getTrackingCode());
        DeliveryEntity entity = DeliveryPersistenceMapper.toEntity(delivery, parcel);
        assertThat(entity.getTrackingCode()).isNull();

        Delivery rehydrated = DeliveryPersistenceMapper.toDomain(entity, parcel);
        assertThat(rehydrated.getTrackingCode()).isEqualTo(parcel.getTrackingCode());

        // a code printed before D-136 stays on the delivery row and still resolves when the parcel has none
        entity.setTrackingCode("TNT-20250101-OLDCODE1");
        Parcel legacyParcel = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel.builder()
                .id(parcel.getId()).specification(parcel.getSpecification()).createdAt(parcel.getCreatedAt())
                .version(1L).build();
        assertThat(DeliveryPersistenceMapper.toDomain(entity, legacyParcel).getTrackingCode())
                .isEqualTo("TNT-20250101-OLDCODE1");
    }

    @Test
    @DisplayName("agreed price and windows survive entity <-> aggregate mapping")
    void agreedTermsSurviveRoundTrip() {
        Parcel parcel = Parcel.create(new PackageSpecification(2.5, 30, 20, 15, false, false, "Test parcel"));
        Delivery delivery = Delivery.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), parcel,
                new DeliveryAddress("Rue A", null, "District", "Douala", "CM", null),
                new DeliveryAddress("Rue B", null, "District", "Douala", "CM", null),
                new RecipientInfo("Jean Dupont", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
        java.time.Instant t = java.time.Instant.parse("2026-11-24T08:00:00Z");
        delivery.recordAgreedTerms(new java.math.BigDecimal("3200.00"), "XAF",
                t, t.plusSeconds(4 * 3600), t.plusSeconds(3600), t.plusSeconds(24 * 3600));

        DeliveryEntity entity = DeliveryPersistenceMapper.toEntity(delivery, parcel);
        assertThat(entity.getAgreedAmount()).isEqualByComparingTo("3200");
        assertThat(entity.getPickupWindowStart()).isEqualTo(t);

        Delivery back = DeliveryPersistenceMapper.toDomain(entity, parcel);
        assertThat(back.getAgreedAmount()).isEqualByComparingTo("3200");
        assertThat(back.getAgreedCurrency()).isEqualTo("XAF");
        assertThat(back.getPickupWindowEnd()).isEqualTo(t.plusSeconds(4 * 3600));
        assertThat(back.getDeliveryWindowStart()).isEqualTo(t.plusSeconds(3600));
        assertThat(back.getDeliveryWindowEnd()).isEqualTo(t.plusSeconds(24 * 3600));
    }
}
