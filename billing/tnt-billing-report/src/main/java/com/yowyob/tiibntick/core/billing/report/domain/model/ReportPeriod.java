package com.yowyob.tiibntick.core.billing.report.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Value Object: ReportPeriod.
 * Represents a date range (from inclusive, to inclusive) used for financial reports.
 *
 * @author MANFOUO Braun
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public ReportPeriod {
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to: " + from + " > " + to);
        }
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    public static ReportPeriod of(LocalDate from, LocalDate to) {
        return new ReportPeriod(from, to);
    }

    public static ReportPeriod ofMonth(YearMonth month) {
        return new ReportPeriod(month.atDay(1), month.atEndOfMonth());
    }

    public static ReportPeriod ofCurrentMonth() {
        return ofMonth(YearMonth.now());
    }

    public static ReportPeriod ofYear(int year) {
        return new ReportPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public static ReportPeriod ofCurrentYear() {
        return ofYear(LocalDate.now().getYear());
    }

    /** Returns the period duration in days. */
    public long daysCount() {
        return to.toEpochDay() - from.toEpochDay() + 1;
    }

    @Override
    public String toString() {
        return from + " to " + to;
    }
}
