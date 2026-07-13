package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum PaymentMethod {
    MOBILE_MONEY,
    MTN_MOMO,
    ORANGE_MONEY,
    STRIPE,
    CASH;

    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod m : values()) {
            if (m.name().equalsIgnoreCase(value)) return m;
        }
        return MOBILE_MONEY;
    }
}
