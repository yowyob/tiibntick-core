package com.yowyob.tiibntick.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TiiBnTick date/time utilities for African operational timezones.
 *
 * <p>Provides named constants and convenience methods for the three main African
 * timezones relevant to TiiBnTick markets:
 * <ul>
 *   <li>{@link #AFRICA_DOUALA} — WAT UTC+1 (Cameroon, Central Africa)</li>
 *   <li>{@link #AFRICA_LAGOS}  — WAT UTC+1 (Nigeria, West Africa)</li>
 *   <li>{@link #AFRICA_NAIROBI}— EAT UTC+3 (Kenya, East Africa)</li>
 * </ul>
 *
 * <p>All methods that produce database-ready values return UTC {@link Instant}.
 * Methods that produce display values accept an explicit timezone parameter.
 *
 * <p>This utility has no equivalent in the Yowyob Kernel and is specific to
 * TiiBnTick's African market deployment context.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public final class TntDateUtils {

    /** Central Africa Time — Cameroon, Central African Republic, Chad. UTC+1. */
    public static final ZoneId AFRICA_DOUALA = ZoneId.of("Africa/Douala");

    /** West Africa Time — Nigeria, Benin, Niger. UTC+1. */
    public static final ZoneId AFRICA_LAGOS = ZoneId.of("Africa/Lagos");

    /** East Africa Time — Kenya, Uganda, Tanzania, Ethiopia. UTC+3. */
    public static final ZoneId AFRICA_NAIROBI = ZoneId.of("Africa/Nairobi");

    /** UTC — always used for storage and Kafka message timestamps. */
    public static final ZoneId UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter ISO_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(UTC);

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter DISPLAY_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Prevent instantiation
    private TntDateUtils() {}

    // ─── Current time ────────────────────────────────────────────────────

    /**
     * Returns the current instant in UTC.
     * Use this for ALL timestamps to be persisted in the database.
     */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /**
     * Returns the current date in Douala local time (Cameroon).
     */
    public static LocalDate todayInDouala() {
        return LocalDate.now(AFRICA_DOUALA);
    }

    /**
     * Returns the current date in Lagos local time (Nigeria).
     */
    public static LocalDate todayInLagos() {
        return LocalDate.now(AFRICA_LAGOS);
    }

    /**
     * Returns the current date in Nairobi local time (Kenya).
     */
    public static LocalDate todayInNairobi() {
        return LocalDate.now(AFRICA_NAIROBI);
    }

    // ─── Conversion helpers ──────────────────────────────────────────────

    /**
     * Converts a UTC instant to a local {@link ZonedDateTime} in Douala time.
     * Useful for display, not for persistence.
     */
    public static ZonedDateTime toDouala(Instant instant) {
        return instant.atZone(AFRICA_DOUALA);
    }

    /**
     * Converts a UTC instant to a local {@link ZonedDateTime} in Lagos time.
     */
    public static ZonedDateTime toLagos(Instant instant) {
        return instant.atZone(AFRICA_LAGOS);
    }

    /**
     * Converts a UTC instant to a local {@link ZonedDateTime} in Nairobi time.
     */
    public static ZonedDateTime toNairobi(Instant instant) {
        return instant.atZone(AFRICA_NAIROBI);
    }

    // ─── Boundary helpers ────────────────────────────────────────────────

    /**
     * Returns the start of the given day in the specified zone, as UTC instant.
     * Useful for date-range queries.
     *
     * @param date   the local date
     * @param zoneId the local timezone (use one of the constants above)
     * @return UTC instant at midnight of that local day
     */
    public static Instant startOfDayUtc(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    /**
     * Returns the end of the given day (23:59:59.999999999) in the specified zone, as UTC instant.
     */
    public static Instant endOfDayUtc(LocalDate date, ZoneId zoneId) {
        return date.atTime(23, 59, 59, 999_999_999).atZone(zoneId).toInstant();
    }

    /**
     * Returns the start of today in Douala time, as UTC instant.
     * Convenience shorthand for Cameroonian business-day filters.
     */
    public static Instant startOfTodayDouala() {
        return startOfDayUtc(LocalDate.now(AFRICA_DOUALA), AFRICA_DOUALA);
    }

    /**
     * Returns the end of today in Douala time, as UTC instant.
     */
    public static Instant endOfTodayDouala() {
        return endOfDayUtc(LocalDate.now(AFRICA_DOUALA), AFRICA_DOUALA);
    }

    // ─── Formatting helpers ───────────────────────────────────────────────

    /**
     * Formats an instant as ISO-8601 UTC string (e.g., "2025-10-01T14:30:00Z").
     * For API responses and log messages.
     */
    public static String formatIso(Instant instant) {
        return ISO_FORMAT.format(instant);
    }

    /**
     * Formats an instant as a local date string in the given zone (e.g., "2025-10-01").
     */
    public static String formatDate(Instant instant, ZoneId zoneId) {
        return DATE_FORMAT.format(instant.atZone(zoneId));
    }

    /**
     * Formats an instant for human display in the given zone (e.g., "01/10/2025 14:30").
     * French date format (DD/MM/YYYY) used in Cameroon.
     */
    public static String formatDisplay(Instant instant, ZoneId zoneId) {
        return DISPLAY_FORMAT.format(instant.atZone(zoneId));
    }
}
