package com.yowyob.tiibntick.common.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Single masking point of the personal data published in the aggregate events — decision [G.3] of
 * 2026-11-26, {@code cible-evenements-colis-livraison.md} §3.4:
 * <ul>
 *   <li>no phone number, no e-mail, no street, no landmark: those fields are simply never put in a
 *       snapshot (the exact details are served by the API, with rights);</li>
 *   <li>an address keeps only district, city, country and coordinates rounded to
 *       {@code tnt.kafka.masking.coordinate-decimals} decimals — <b>2</b> (≈ 1.1 km) by decision QO-17
 *       (registre D-125);</li>
 *   <li>a person's name becomes "first name + initial of the last name" ({@code "Awa N."}).</li>
 * </ul>
 * Thread-safe and immutable; one bean per application ({@code tnt-bootstrap} builds it from the property).
 */
public final class TntEventMasking {

    /** Property holding the number of decimals of the published coordinates. */
    public static final String COORDINATE_DECIMALS_PROPERTY = "tnt.kafka.masking.coordinate-decimals";
    /** Decision QO-17 (registre D-125): 2 decimals, about 1.1 km. */
    public static final int DEFAULT_COORDINATE_DECIMALS = 2;
    /** Smallest and largest precision offered by QO-17 (1 decimal ≈ 11 km … 3 decimals ≈ 110 m). */
    public static final int MIN_COORDINATE_DECIMALS = 1;
    public static final int MAX_COORDINATE_DECIMALS = 3;

    private final int coordinateDecimals;

    /**
     * @param coordinateDecimals decimals kept on published coordinates, within the range offered by QO-17
     * @throws IllegalArgumentException outside [{@value #MIN_COORDINATE_DECIMALS}, {@value #MAX_COORDINATE_DECIMALS}]
     *                                  — more decimals would publish an exact address
     */
    public TntEventMasking(final int coordinateDecimals) {
        if (coordinateDecimals < MIN_COORDINATE_DECIMALS || coordinateDecimals > MAX_COORDINATE_DECIMALS) {
            throw new IllegalArgumentException(COORDINATE_DECIMALS_PROPERTY + " must be between "
                + MIN_COORDINATE_DECIMALS + " and " + MAX_COORDINATE_DECIMALS + " (QO-17), got " + coordinateDecimals);
        }
        this.coordinateDecimals = coordinateDecimals;
    }

    /** @return the masking of decision QO-17 (2 decimals) */
    public static TntEventMasking withDefaults() {
        return new TntEventMasking(DEFAULT_COORDINATE_DECIMALS);
    }

    public int coordinateDecimals() {
        return coordinateDecimals;
    }

    // ── Coordinates ───────────────────────────────────────────────────────────────────────────

    /** @return {@code coordinate} rounded half-up to the configured decimals, {@code null} if absent */
    public Double approx(final Double coordinate) {
        if (coordinate == null || coordinate.isNaN() || coordinate.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(coordinate).setScale(coordinateDecimals, RoundingMode.HALF_UP).doubleValue();
    }

    // ── Addresses ─────────────────────────────────────────────────────────────────────────────

    /**
     * Masked address of a snapshot (street, landmark, postal code and exact position dropped).
     *
     * @return the masked address; never {@code null} (all fields may be null)
     */
    public MaskedAddress address(final String district, final String city, final String country,
                                 final Double latitude, final Double longitude) {
        return new MaskedAddress(blankToNull(district), blankToNull(city), blankToNull(country),
            approx(latitude), approx(longitude));
    }

    /** Same as {@link #address(String, String, String, Double, Double)} from a coordinates value object. */
    public MaskedAddress address(final String district, final String city, final String country,
                                 final GeoCoordinates coordinates) {
        return coordinates == null
            ? address(district, city, country, null, null)
            : address(district, city, country, coordinates.getLatitude(), coordinates.getLongitude());
    }

    /** Masked form of the shared {@link Address} value object (its {@code quarter} is the district). */
    public MaskedAddress address(final Address address) {
        if (address == null) {
            return null;
        }
        return address(address.getQuarter().orElse(null), address.getCity(), address.getCountry(),
            address.getCoordinates().orElse(null));
    }

    // ── Names, amounts ────────────────────────────────────────────────────────────────────────

    /**
     * "First name + initial of the last name" from a full name: {@code "Awa Ndiaye"} → {@code "Awa N."},
     * {@code "Jean Paul Mbarga"} → {@code "Jean M."}. A single word is returned alone (no initial to add).
     *
     * @return the display name, {@code null} if {@code fullName} is blank
     */
    public static String displayName(final String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] words = fullName.trim().split("\\s+");
        return words.length == 1 ? words[0] : displayName(words[0], words[words.length - 1]);
    }

    /** {@code ("Awa", "Ndiaye")} → {@code "Awa N."}; either part may be blank. */
    public static String displayName(final String firstName, final String lastName) {
        String first = blankToNull(firstName == null ? null : firstName.trim());
        String last = blankToNull(lastName == null ? null : lastName.trim());
        if (first == null && last == null) {
            return null;
        }
        if (last == null) {
            return first.split("\\s+")[0];
        }
        String initial = new String(Character.toChars(last.codePointAt(0))).toUpperCase(Locale.ROOT) + ".";
        return first == null ? initial : first.split("\\s+")[0] + " " + initial;
    }

    /** Amounts are published as decimal strings (spec §3.3): {@code 1500.50} → {@code "1500.50"}. */
    public static String amount(final BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * {@code $defs/maskedAddress} of {@code envelope.schema.json}: district, city, country and rounded
     * coordinates only.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MaskedAddress(String district, String city, String country,
                                Double approxLatitude, Double approxLongitude) {
    }
}
