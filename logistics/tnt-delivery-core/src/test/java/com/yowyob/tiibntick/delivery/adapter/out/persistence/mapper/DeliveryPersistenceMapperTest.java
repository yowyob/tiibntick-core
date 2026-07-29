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
    @DisplayName("toEntity() then toDomain() preserves the generated tracking code")
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

        DeliveryEntity entity = DeliveryPersistenceMapper.toEntity(delivery, parcel);
        assertThat(entity.getTrackingCode()).isEqualTo(delivery.getTrackingCode());

        Delivery rehydrated = DeliveryPersistenceMapper.toDomain(entity, parcel);
        assertThat(rehydrated.getTrackingCode()).isEqualTo(delivery.getTrackingCode());
    }
}
