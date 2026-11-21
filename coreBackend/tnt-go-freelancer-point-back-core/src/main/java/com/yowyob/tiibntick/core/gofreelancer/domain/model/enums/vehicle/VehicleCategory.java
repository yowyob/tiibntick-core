package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle;

import java.util.Locale;

/**
 * Lot C-54 — the five vehicle categories the Go app shows ({@code moto | tricycle | taxi | camion |
 * bus}) and their single, explicit correspondence with the core {@link VehicleType}.
 *
 * <pre>
 *   app word   canonical VehicleType   also accepted (same category)
 *   moto       MOTORBIKE               SCOOTER, BIKE
 *   tricycle   TRICYCLE                —
 *   taxi       CAR                     —
 *   camion     TRUCK                   VAN
 *   bus        BUS                     —
 * </pre>
 *
 * <p>A category is what the platform grid and the "required vehicle" filter work on: a VAN courier
 * is proposed for a "camion" delivery and priced on the camion grid.
 */
public enum VehicleCategory {
    MOTO(VehicleType.MOTORBIKE, "moto"),
    TRICYCLE(VehicleType.TRICYCLE, "tricycle"),
    TAXI(VehicleType.CAR, "taxi"),
    CAMION(VehicleType.TRUCK, "camion"),
    BUS(VehicleType.BUS, "bus");

    private final VehicleType canonical;
    private final String appWord;

    VehicleCategory(VehicleType canonical, String appWord) {
        this.canonical = canonical;
        this.appWord = appWord;
    }

    public VehicleType canonical() { return canonical; }

    public String appWord() { return appWord; }

    public static VehicleCategory of(VehicleType type) {
        if (type == null) return null;
        return switch (type) {
            case MOTORBIKE, SCOOTER, BIKE -> MOTO;
            case TRICYCLE -> TRICYCLE;
            case CAR -> TAXI;
            case TRUCK, VAN -> CAMION;
            case BUS -> BUS;
        };
    }

    /**
     * Either vocabulary (app word or core type name, any case) → category; {@code null} for a blank,
     * {@code "ANY"}/{@code "peu importe"} or unknown value.
     */
    public static VehicleCategory parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (VehicleCategory c : values()) {
            if (c.appWord.equals(v) || c.name().equalsIgnoreCase(v)) return c;
        }
        for (VehicleType t : VehicleType.values()) {
            if (t.name().equalsIgnoreCase(v)) return of(t);
        }
        return null;
    }

    /** Either vocabulary → canonical core type name, or {@code null}. */
    public static String canonicalName(String raw) {
        VehicleCategory c = parse(raw);
        return c == null ? null : c.canonical.name();
    }
}
