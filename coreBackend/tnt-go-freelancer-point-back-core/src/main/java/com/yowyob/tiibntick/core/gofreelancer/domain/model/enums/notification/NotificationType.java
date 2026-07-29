package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification;

/**
 * Represents the type of notification sent to users.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
public enum NotificationType {

    REGISTERED_PARCEL("REGISTERED_PARCEL"),
    PARCEL_IN_TRANSIT("PARCEL_IN_TRANSIT"),
    PARCEL_DELIVERED("PARCEL_DELIVERED"),
    DELIVERY_PARCEL("DELIVERY_PARCEL"),
    NEW_ANNOUNCEMENT("NEW_ANNOUNCEMENT"),
    EVALUATION_REQUEST("EVALUATION_REQUEST");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NotificationType fromValue(String value) {
        for (NotificationType type : NotificationType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type: " + value);
    }
}
