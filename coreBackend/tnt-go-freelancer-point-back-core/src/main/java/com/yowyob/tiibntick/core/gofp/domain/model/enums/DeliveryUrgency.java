package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum DeliveryUrgency {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public static DeliveryUrgency fromValue(String value) {
        for (DeliveryUrgency u : values()) {
            if (u.name().equalsIgnoreCase(value)) return u;
        }
        return NORMAL;
    }
}
