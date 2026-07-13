package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum VehicleType {
    MOTO,
    VOITURE,
    CAMION,
    VELO,
    TRICYCLE,
    CAMIONNETTE,
    AUTRE;

    public static VehicleType fromValue(String value) {
        if (value == null) return null;
        for (VehicleType t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        return AUTRE;
    }
}
