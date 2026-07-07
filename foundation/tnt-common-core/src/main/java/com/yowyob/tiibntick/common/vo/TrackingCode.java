package com.yowyob.tiibntick.common.vo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TiiBnTick tracking code — immutable value object.
 *
 * <p>Format: {@code PREFIX-YYYYMMDD-NNNNNN}
 * <ul>
 *   <li>PREFIX — 2 to 5 uppercase letters (e.g., TNT, DEL, PKG, HUB)</li>
 *   <li>YYYYMMDD — date part (ISO date without separators)</li>
 *   <li>NNNNNN — 6 alphanumeric characters (uppercase, random or sequential)</li>
 * </ul>
 * Examples: {@code TNT-20251001-A3BX92}, {@code DEL-20251015-000042}, {@code PKG-20251020-F7KQ21}
 *
 * <p>This VO has no equivalent in the Yowyob Kernel. It is specific to TiiBnTick
 * and is used as the customer-facing parcel/mission tracking identifier.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public final class TrackingCode {

    private static final Pattern VALID_PATTERN =
        Pattern.compile("^[A-Z]{2,5}-\\d{8}-[A-Z0-9]{6}$");

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final String value;

    private TrackingCode(String value) {
        this.value = Objects.requireNonNull(value, "TrackingCode value must not be null").toUpperCase();
    }

    // ─── Factory methods ────────────────────────────────────────────────

    /**
     * Generates a new random tracking code for the given prefix.
     *
     * @param prefix 2–5 uppercase letter prefix (e.g., "TNT", "DEL", "PKG")
     * @return a new TrackingCode with today's date and a random 6-char suffix
     */
    public static TrackingCode generate(String prefix) {
        validatePrefix(prefix);
        String date = LocalDate.now().format(DATE_FORMAT);
        String suffix = randomSuffix(6);
        return new TrackingCode(prefix.toUpperCase() + "-" + date + "-" + suffix);
    }

    /**
     * Generates a tracking code with a zero-padded sequential number.
     * Useful for deterministic codes when a sequence number is available.
     *
     * @param prefix         2–5 uppercase letter prefix
     * @param sequenceNumber positive sequential number (zero-padded to 6 digits)
     * @return a new TrackingCode with a sequential suffix
     */
    public static TrackingCode generateSequential(String prefix, long sequenceNumber) {
        validatePrefix(prefix);
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("Sequence number must be non-negative");
        }
        String date = LocalDate.now().format(DATE_FORMAT);
        String suffix = String.format("%06d", sequenceNumber % 1_000_000);
        return new TrackingCode(prefix.toUpperCase() + "-" + date + "-" + suffix);
    }

    /**
     * Parses an existing tracking code string.
     *
     * @param value the raw string (e.g., "TNT-20251001-A3BX92")
     * @return a TrackingCode instance
     * @throws IllegalArgumentException if the value does not match the expected format
     */
    public static TrackingCode of(String value) {
        if (value == null || !VALID_PATTERN.matcher(value.toUpperCase()).matches()) {
            throw new IllegalArgumentException(
                "Invalid TrackingCode format: '" + value + "'. Expected: PREFIX-YYYYMMDD-NNNNNN");
        }
        return new TrackingCode(value);
    }

    /**
     * Returns {@code true} if the given string is a valid tracking code.
     */
    public static boolean isValid(String value) {
        return value != null && VALID_PATTERN.matcher(value.toUpperCase()).matches();
    }

    // ─── Accessors ──────────────────────────────────────────────────────

    /** Returns the full tracking code string (e.g., "TNT-20251001-A3BX92"). */
    public String getValue() {
        return value;
    }

    /** Returns the prefix part (e.g., "TNT"). */
    public String getPrefix() {
        return value.split("-")[0];
    }

    /** Returns the date part as string (e.g., "20251001"). */
    public String getDatePart() {
        return value.split("-")[1];
    }

    /** Returns the suffix part (e.g., "A3BX92"). */
    public String getSuffix() {
        return value.split("-")[2];
    }

    // ─── Equality and string representation ─────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackingCode)) return false;
        return value.equals(((TrackingCode) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static void validatePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("TrackingCode prefix must not be blank");
        }
        String upper = prefix.toUpperCase();
        if (!upper.matches("[A-Z]{2,5}")) {
            throw new IllegalArgumentException(
                "TrackingCode prefix must be 2–5 uppercase letters, got: '" + prefix + "'");
        }
    }

    private static String randomSuffix(int length) {
        // UUID-based randomness — no SecureRandom overhead, sufficient for tracking codes
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = Character.digit(uuid.charAt(i), 16) % ALPHANUM.length();
            sb.append(ALPHANUM.charAt(Math.abs(idx)));
        }
        return sb.toString();
    }
}
