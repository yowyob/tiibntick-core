package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public final class AvailabilitySlot {

    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private AvailabilitySlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    public static AvailabilitySlot of(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return new AvailabilitySlot(dayOfWeek, startTime, endTime);
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }

    public boolean overlaps(AvailabilitySlot other) {
        if (this.dayOfWeek != other.dayOfWeek) return false;
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AvailabilitySlot other)) return false;
        return dayOfWeek == other.dayOfWeek
                && startTime.equals(other.startTime)
                && endTime.equals(other.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayOfWeek, startTime, endTime);
    }

    @Override
    public String toString() {
        return "AvailabilitySlot{" + dayOfWeek + " " + startTime + "-" + endTime + "}";
    }
}
