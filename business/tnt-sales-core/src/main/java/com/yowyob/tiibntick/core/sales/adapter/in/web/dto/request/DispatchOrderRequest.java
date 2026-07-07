package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to dispatch an order to a delivery mission.
 * Author: MANFOUO Braun
 */
public record DispatchOrderRequest(@NotNull UUID missionId) {}
