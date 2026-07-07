package com.yowyob.tiibntick.core.sales.adapter.in.web.dto.request;

import com.yowyob.tiibntick.core.sales.domain.model.ReturnReason;
import jakarta.validation.constraints.NotNull;

/** Request to return an order. Author: MANFOUO Braun */
public record ReturnOrderRequest(@NotNull ReturnReason reason, String note) {}
