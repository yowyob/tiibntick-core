package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;

/**
 * Manual mapper for {@code Parcel} ↔ {@code ParcelEntity}.
 *
 * @author MANFOUO Braun
 */
public final class ParcelPersistenceMapper {

    private ParcelPersistenceMapper() {}

    public static ParcelEntity toEntity(Parcel p) {
        if (p == null) return null;
        PackageSpecification spec = p.getSpecification();
        return ParcelEntity.builder()
                .id(p.getId())
                .weightKg(spec.weightKg())
                .widthCm(spec.widthCm())
                .heightCm(spec.heightCm())
                .lengthCm(spec.lengthCm())
                .fragile(spec.fragile())
                .perishable(spec.perishable())
                .description(spec.description())
                .photoUrl(p.getPhotoUrl())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .version(p.getVersion())
                .build();
    }

    public static Parcel toDomain(ParcelEntity e) {
        if (e == null) return null;
        PackageSpecification spec = new PackageSpecification(
                e.getWeightKg(), e.getWidthCm(), e.getHeightCm(), e.getLengthCm(),
                Boolean.TRUE.equals(e.getFragile()), Boolean.TRUE.equals(e.getPerishable()),
                e.getDescription());
        return Parcel.builder()
                .id(e.getId())
                .specification(spec)
                .photoUrl(e.getPhotoUrl())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }
}
