package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment;

/**
 * Defines the supported payment methods.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum PaymentMethod {

    MOBILE_MONEY("MOBILE_MONEY"),
    ORANGE_MONEY("ORANGE_MONEY"),
    CASH("CASH");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }
}