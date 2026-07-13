package com.yowyob.tiibntick.core.marketback.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Value Object — availability schedule of a ServiceOffer.
 * @author MANFOUO Braun
 */
public record OfferAvailability(
        Set<DayOfWeek> daysOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        List<LocalDate> exceptionalClosures,
        boolean expressAvailable,
        boolean sameDayAvailable
) {
    public boolean isAvailableOn(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        if (exceptionalClosures != null && exceptionalClosures.contains(date)) return false;
        if (daysOfWeek != null && !daysOfWeek.contains(dateTime.getDayOfWeek())) return false;
        LocalTime time = dateTime.toLocalTime();
        if (openTime != null && time.isBefore(openTime)) return false;
        if (closeTime != null && time.isAfter(closeTime)) return false;
        return true;
    }
}
