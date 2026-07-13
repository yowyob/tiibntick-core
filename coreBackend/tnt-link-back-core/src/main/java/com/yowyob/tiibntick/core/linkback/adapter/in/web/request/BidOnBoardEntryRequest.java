package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BidOnBoardEntryRequest(
        @NotNull Instant estimatedArrivalTime,
        String note
) {
}
