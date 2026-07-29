package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery;

/**
 * Defines the urgency level of a delivery.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum DeliveryUrgency {

    NORMAL("NORMAL"),
    EXPRESS("EXPRESS"),
    STANDARD("STANDARD");

    private final String value;

    DeliveryUrgency(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeliveryUrgency fromValue(String value) {
        for (DeliveryUrgency urgency : DeliveryUrgency.values()) {
            if (urgency.value.equalsIgnoreCase(value)) {
                return urgency;
            }
        }
        throw new IllegalArgumentException("Unknown delivery urgency: " + value);
    }
}