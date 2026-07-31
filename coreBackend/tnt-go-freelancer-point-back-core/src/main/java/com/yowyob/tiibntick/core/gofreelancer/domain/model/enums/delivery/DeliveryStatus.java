package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery;

/**
 * Defines the execution status of a delivery.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum DeliveryStatus {

    CREATED("CREATED"),
    PICKED_UP("PICKED_UP"),
    IN_TRANSIT("IN_TRANSIT"),
    /** Parcel deposited at relay hub — aligns with delivery-core {@code AT_RELAY_POINT}. */
    AT_RELAY_POINT("AT_RELAY_POINT"),
    DELIVERED("DELIVERED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    DeliveryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeliveryStatus fromValue(String value) {
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown delivery status: " + value);
    }
}