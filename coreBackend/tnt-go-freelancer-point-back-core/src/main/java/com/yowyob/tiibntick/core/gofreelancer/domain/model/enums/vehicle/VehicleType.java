package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle;

/**
 * Defines the type of vehicle owned by a freelancer.
 */
public enum VehicleType {

    BIKE, MOTORBIKE, CAR, VAN, TRUCK, SCOOTER,
    /** Lot C-54 — motorised tricycle (cargo or passenger), priced on its own platform grid. */
    TRICYCLE,
    /** Lot C-54 — bus / minibus, priced on its own platform grid. */
    BUS;

    public String getValue() { return name(); }

    public static VehicleType fromValue(String value) {
        for (VehicleType t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("Unknown vehicle type: " + value);
    }
}
