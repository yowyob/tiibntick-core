package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement;

/**
 * Defines the lifecycle status of an announcement.
 */
public enum AnnouncementStatus {

    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    IN_NEGOTIATION("IN_NEGOTIATION"),
    ASSIGNED("ASSIGNED"),
    CANCELLED("CANCELLED"),
    COMPLETED("COMPLETED");

    private final String value;

    AnnouncementStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AnnouncementStatus fromValue(String value) {
        for (AnnouncementStatus status : AnnouncementStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown announcement status: " + value);
    }
}
