package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.notification;

/**
 * Represents the status of a notification.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
public enum NotificationStatus {

    PENDING("PENDING"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    READ("READ"),
    FAILED("FAILED");

    private final String value;

    NotificationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NotificationStatus fromValue(String value) {
        for (NotificationStatus status : NotificationStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown notification status: " + value);
    }
}
