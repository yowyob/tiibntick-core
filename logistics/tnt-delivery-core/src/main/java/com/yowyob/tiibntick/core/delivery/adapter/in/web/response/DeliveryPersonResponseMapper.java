package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;

/**
 * Mapper from {@code DeliveryPerson} domain aggregate to HTTP response DTO.
 *
 * @author MANFOUO Braun
 */
public final class DeliveryPersonResponseMapper {

    private DeliveryPersonResponseMapper() {}

    public static DeliveryPersonResponse toResponse(DeliveryPerson dp) {
        GeoCoordinates loc = dp.getCurrentLocation();
        return new DeliveryPersonResponse(
                dp.getId(),
                dp.getTenantId(),
                dp.getActorId(),
                dp.getLogisticsType(),
                dp.getLogisticsClass(),
                dp.getTankCapacity(),
                dp.getColor(),
                dp.getTotalDeliveries(),
                dp.getFailedDeliveries(),
                loc != null ? loc.latitude() : null,
                loc != null ? loc.longitude() : null,
                dp.getStatus(),
                dp.getCreatedAt());
    }
}
