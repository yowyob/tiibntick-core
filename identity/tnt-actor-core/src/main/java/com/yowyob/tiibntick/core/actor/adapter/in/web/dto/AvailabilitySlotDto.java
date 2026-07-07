package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.AvailabilitySlot;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilitySlotDto(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {

    public AvailabilitySlot toDomain() {
        return AvailabilitySlot.of(dayOfWeek, startTime, endTime);
    }

    public static AvailabilitySlotDto from(AvailabilitySlot slot) {
        return new AvailabilitySlotDto(slot.dayOfWeek(), slot.startTime(), slot.endTime());
    }
}
