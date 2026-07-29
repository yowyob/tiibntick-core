package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle;

/**
 * Defines the capacity/condition class of a vehicle or store.
 *
 * @author TiiBnTickTeam
 * @date 08/07/2026
 */
public enum VehicleClass {

    STANDARD("STANDARD"),
    DAMAGED("DAMAGED"),
    PREMIUM("PREMIUM"),
    RESTRICTED("RESTRICTED");

    private final String value;

    VehicleClass(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VehicleClass fromValue(String value) {
        for (VehicleClass vehicleClass : VehicleClass.values()) {
            if (vehicleClass.value.equalsIgnoreCase(value)) {
                return vehicleClass;
            }
        }
        throw new IllegalArgumentException("Unknown vehicle class: " + value);
    }
}
