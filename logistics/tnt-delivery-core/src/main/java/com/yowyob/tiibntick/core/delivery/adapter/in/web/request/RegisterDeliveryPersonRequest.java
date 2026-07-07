package com.yowyob.tiibntick.core.delivery.adapter.in.web.request;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * HTTP request body for registering a new delivery person.
 *
 * @author MANFOUO Braun
 */
public record RegisterDeliveryPersonRequest(
        @NotNull UUID actorId,
        @NotNull LogisticsType logisticsType,
        @NotNull LogisticsClass logisticsClass,
        @Positive double tankCapacity,
        double grossFloor,
        int totalSeatNumber,
        String color,
        String commercialRegisterNumber
) {}
