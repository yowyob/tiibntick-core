package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.payment;

/**
 * Defines the payment processing status.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum PaymentStatus {

    PENDING("PENDING"),
    PAID("PAID"),
    FAILED("FAILED"),
    REFUNDED("REFUNDED");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PaymentStatus fromValue(String value) {
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }
}