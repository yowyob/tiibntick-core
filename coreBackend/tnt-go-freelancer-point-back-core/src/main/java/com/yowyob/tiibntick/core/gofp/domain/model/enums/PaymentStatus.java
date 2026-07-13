package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    DISPUTED;

    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        return PENDING;
    }
}
