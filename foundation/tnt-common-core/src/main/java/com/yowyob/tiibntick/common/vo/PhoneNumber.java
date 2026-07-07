package com.yowyob.tiibntick.common.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * African phone number — immutable value object stored in E.164 format.
 *
 * <p>Provides TiiBnTick-specific helpers for Cameroonian and other African markets:
 * <ul>
 *   <li>{@link #ofCameroon(String)} — normalizes local Cameroonian numbers to E.164</li>
 *   <li>{@link #ofNigeria(String)} — normalizes local Nigerian numbers to E.164</li>
 *   <li>{@link #getMasked()} — GDPR-compliant masked representation</li>
 * </ul>
 *
 * <p>This VO has no equivalent in the Yowyob Kernel. It is specific to TiiBnTick's
 * African market context where phone numbers are primary identifiers for clients
 * and deliverers (many users have no email address).
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public final class PhoneNumber {

    /** E.164 format: +[country code][subscriber number], max 15 digits. */
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    /** Cameroon country code: +237. Local numbers are 9 digits starting with 6 or 2. */
    private static final String CAMEROON_CODE = "+237";

    /** Nigeria country code: +234. Local numbers start with 0 + 10 digits. */
    private static final String NIGERIA_CODE = "+234";

    /** Kenya country code: +254. */
    private static final String KENYA_CODE = "+254";

    /** Ivory Coast country code: +225. */
    private static final String IVORY_COAST_CODE = "+225";

    private final String e164Value;

    private PhoneNumber(String e164Value) {
        this.e164Value = Objects.requireNonNull(e164Value, "PhoneNumber value must not be null");
    }

    // ─── Factory methods ────────────────────────────────────────────────

    /**
     * Creates a PhoneNumber from an E.164 string.
     *
     * @param e164 phone number in E.164 format (e.g., "+237677123456")
     * @throws IllegalArgumentException if the string is not valid E.164
     */
    public static PhoneNumber of(String e164) {
        if (e164 == null || !E164_PATTERN.matcher(e164).matches()) {
            throw new IllegalArgumentException(
                "Phone number must be in E.164 format (e.g., +237677123456), got: '" + e164 + "'");
        }
        return new PhoneNumber(e164);
    }

    /**
     * Normalizes a Cameroonian phone number to E.164 (+237).
     * Accepts:
     * <ul>
     *   <li>Local 9-digit format: "677123456" → "+237677123456"</li>
     *   <li>Already E.164: "+237677123456" → unchanged</li>
     *   <li>With leading zero: "0677123456" → "+237677123456" (treated as local)</li>
     * </ul>
     *
     * @param localNumber Cameroonian number in any accepted format
     * @return PhoneNumber in E.164 format
     */
    public static PhoneNumber ofCameroon(String localNumber) {
        return of(normalizeToE164(localNumber, CAMEROON_CODE, 9));
    }

    /**
     * Normalizes a Nigerian phone number to E.164 (+234).
     * Accepts local 10-digit format with leading zero: "08012345678" → "+2348012345678".
     */
    public static PhoneNumber ofNigeria(String localNumber) {
        return of(normalizeToE164(localNumber, NIGERIA_CODE, 10));
    }

    /**
     * Normalizes a Kenyan phone number to E.164 (+254).
     * Accepts local 9-digit format: "712345678" → "+254712345678".
     */
    public static PhoneNumber ofKenya(String localNumber) {
        return of(normalizeToE164(localNumber, KENYA_CODE, 9));
    }

    /**
     * Normalizes an Ivory Coast phone number to E.164 (+225).
     */
    public static PhoneNumber ofIvoryCoast(String localNumber) {
        return of(normalizeToE164(localNumber, IVORY_COAST_CODE, 10));
    }

    // ─── Accessors ──────────────────────────────────────────────────────

    /**
     * Returns the phone number in E.164 format (e.g., "+237677123456").
     * This is the value to persist in the database.
     */
    public String getValue() {
        return e164Value;
    }

    /**
     * Returns a GDPR-compliant masked representation.
     * Format: country code + first 3 digits + *** + last 3 digits.
     * Example: "+237677123456" → "+237677***456"
     */
    public String getMasked() {
        // Find where subscriber number starts (after country code)
        // E.164: +[1-3 digit country code][subscriber number]
        int subscriberStart = findSubscriberStart();
        String countryCodePart = e164Value.substring(0, subscriberStart);
        String subscriber = e164Value.substring(subscriberStart);

        if (subscriber.length() <= 6) {
            return countryCodePart + "***";
        }
        int visiblePrefix = Math.min(3, subscriber.length() / 3);
        int visibleSuffix = Math.min(3, subscriber.length() / 3);
        String prefix = subscriber.substring(0, visiblePrefix);
        String suffix = subscriber.substring(subscriber.length() - visibleSuffix);
        return countryCodePart + prefix + "***" + suffix;
    }

    /**
     * Returns the country calling code (e.g., "+237" for Cameroon).
     */
    public String getCountryCode() {
        return e164Value.substring(0, findSubscriberStart());
    }

    /**
     * Returns the subscriber number without country code (e.g., "677123456").
     */
    public String getSubscriberNumber() {
        return e164Value.substring(findSubscriberStart());
    }

    /** Returns true if this is a Cameroonian number (+237). */
    public boolean isCameroonian() {
        return e164Value.startsWith(CAMEROON_CODE);
    }

    /** Returns true if this is a Nigerian number (+234). */
    public boolean isNigerian() {
        return e164Value.startsWith(NIGERIA_CODE);
    }

    /** Returns true if the number is valid E.164 format. */
    public static boolean isValid(String value) {
        return value != null && E164_PATTERN.matcher(value).matches();
    }

    // ─── Equality and string representation ─────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhoneNumber)) return false;
        return e164Value.equals(((PhoneNumber) o).e164Value);
    }

    @Override
    public int hashCode() {
        return e164Value.hashCode();
    }

    /**
     * Returns the masked value for safe logging — NEVER returns the raw E.164 value.
     * Use {@link #getValue()} explicitly when the full number is needed.
     */
    @Override
    public String toString() {
        return getMasked();
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private static String normalizeToE164(String raw, String countryCode, int localDigits) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be blank");
        }
        String cleaned = raw.replaceAll("[\\s\\-\\(\\)\\.]+", "");

        // Already E.164 with correct country code
        if (cleaned.startsWith(countryCode)) {
            if (E164_PATTERN.matcher(cleaned).matches()) return cleaned;
            throw new IllegalArgumentException("Invalid phone number for " + countryCode + ": " + raw);
        }

        // Already E.164 with + but different country code
        if (cleaned.startsWith("+")) {
            throw new IllegalArgumentException(
                "Phone number '" + raw + "' has a different country code than " + countryCode);
        }

        // Local format: strip leading zero if present
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }

        if (cleaned.length() != localDigits) {
            throw new IllegalArgumentException(
                "Invalid local phone number length for " + countryCode +
                ": expected " + localDigits + " digits, got " + cleaned.length() + " in '" + raw + "'");
        }

        return countryCode + cleaned;
    }

    private int findSubscriberStart() {
        // Country codes are 1–3 digits after the '+'
        // Heuristic: try 4 chars (+237), then 3 (+33), then 2 (+1)
        String[] knownCodes = {CAMEROON_CODE, NIGERIA_CODE, KENYA_CODE, IVORY_COAST_CODE,
            "+33", "+44", "+1", "+49", "+27", "+255", "+256", "+250", "+221"};
        for (String code : knownCodes) {
            if (e164Value.startsWith(code)) return code.length();
        }
        // Fallback: try 4-char country code
        return Math.min(4, e164Value.length());
    }
}
