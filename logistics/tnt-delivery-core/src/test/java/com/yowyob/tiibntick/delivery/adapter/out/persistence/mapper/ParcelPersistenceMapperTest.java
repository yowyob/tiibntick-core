package com.yowyob.tiibntick.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelPersistenceMapperTest {

    @Test
    @DisplayName("designation and thickness survive entity <-> aggregate mapping")
    void designationAndThicknessRoundTrip() {
        Parcel parcel = Parcel.create(new PackageSpecification(
                3.0, 25, 15, 40, true, false, "Ceramic plates", "Carton vaisselle", 7.5));
        parcel.attachPhoto("https://files.example/p.jpg");

        ParcelEntity entity = ParcelPersistenceMapper.toEntity(parcel);
        assertThat(entity.getDesignation()).isEqualTo("Carton vaisselle");
        assertThat(entity.getThicknessCm()).isEqualTo(7.5);

        Parcel back = ParcelPersistenceMapper.toDomain(entity);
        assertThat(back.getId()).isEqualTo(parcel.getId());
        assertThat(back.getSpecification()).isEqualTo(parcel.getSpecification());
        assertThat(back.getPhotoUrl()).isEqualTo("https://files.example/p.jpg");
    }

    @Test
    @DisplayName("a row without designation nor thickness (older parcel) still loads")
    void olderRowLoads() {
        ParcelEntity entity = ParcelEntity.builder()
                .id(java.util.UUID.randomUUID())
                .weightKg(1.0).widthCm(10.0).heightCm(10.0).lengthCm(10.0)
                .fragile(false).perishable(false).description("old")
                .build();

        Parcel parcel = ParcelPersistenceMapper.toDomain(entity);

        assertThat(parcel.getSpecification().designation()).isNull();
        assertThat(parcel.getSpecification().thicknessCm()).isNull();
    }
}
