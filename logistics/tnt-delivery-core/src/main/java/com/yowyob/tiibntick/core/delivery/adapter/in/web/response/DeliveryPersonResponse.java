package com.yowyob.tiibntick.core.delivery.adapter.in.web.response;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP response DTO for {@code DeliveryPerson} aggregate.
 *
 * @author MANFOUO Braun
 */
public record DeliveryPersonResponse(
        UUID id,
        UUID tenantId,
        UUID actorId,
        LogisticsType logisticsType,
        LogisticsClass logisticsClass,
        double tankCapacity,
        String color,
        int totalDeliveries,
        int failedDeliveries,
        Double currentLatitude,
        Double currentLongitude,
        DeliveryPersonStatus status,
        Instant createdAt
) {}
