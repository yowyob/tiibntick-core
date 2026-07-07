package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryPersonEntity;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;

/**
 * Manual mapper for {@code DeliveryPerson} ↔ {@code DeliveryPersonEntity}.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryPersonPersistenceMapper {

    private DeliveryPersonPersistenceMapper() {}

    public static DeliveryPersonEntity toEntity(DeliveryPerson dp) {
        GeoCoordinates loc = dp.getCurrentLocation();
        return DeliveryPersonEntity.builder()
                .id(dp.getId())
                .tenantId(dp.getTenantId())
                .actorId(dp.getActorId())
                .logisticsType(dp.getLogisticsType().name())
                .logisticsClass(dp.getLogisticsClass().name())
                .tankCapacity(dp.getTankCapacity())
                .grossFloor(dp.getGrossFloor())
                .totalSeatNumber(dp.getTotalSeatNumber())
                .color(dp.getColor())
                .commercialRegisterNumber(dp.getCommercialRegisterNumber())
                .remainingDeliveries(dp.getRemainingDeliveries())
                .failedDeliveries(dp.getFailedDeliveries())
                .totalDeliveries(dp.getTotalDeliveries())
                .currentLatitude(loc != null ? loc.latitude() : null)
                .currentLongitude(loc != null ? loc.longitude() : null)
                .locationUpdatedAt(dp.getLocationUpdatedAt())
                .status(dp.getStatus().name())
                .createdAt(dp.getCreatedAt())
                .updatedAt(dp.getUpdatedAt())
                .version(dp.getVersion())
                .build();
    }

    public static DeliveryPerson toDomain(DeliveryPersonEntity e) {
        GeoCoordinates loc = null;
        if (e.getCurrentLatitude() != null && e.getCurrentLongitude() != null) {
            loc = new GeoCoordinates(e.getCurrentLatitude(), e.getCurrentLongitude());
        }
        return DeliveryPerson.builder()
                .id(e.getId())
                .tenantId(e.getTenantId())
                .actorId(e.getActorId())
                .logisticsType(LogisticsType.valueOf(e.getLogisticsType()))
                .logisticsClass(LogisticsClass.valueOf(e.getLogisticsClass()))
                .tankCapacity(e.getTankCapacity())
                .grossFloor(e.getGrossFloor())
                .totalSeatNumber(e.getTotalSeatNumber())
                .color(e.getColor())
                .commercialRegisterNumber(e.getCommercialRegisterNumber())
                .remainingDeliveries(e.getRemainingDeliveries())
                .failedDeliveries(e.getFailedDeliveries())
                .totalDeliveries(e.getTotalDeliveries())
                .currentLocation(loc)
                .locationUpdatedAt(e.getLocationUpdatedAt())
                .status(DeliveryPersonStatus.valueOf(e.getStatus()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .build();
    }
}
