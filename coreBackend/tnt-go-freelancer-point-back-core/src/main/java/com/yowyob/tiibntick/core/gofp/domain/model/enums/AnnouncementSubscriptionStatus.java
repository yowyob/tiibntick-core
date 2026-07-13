package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum AnnouncementSubscriptionStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    WITHDRAWN;

    public static AnnouncementSubscriptionStatus fromValue(String value) {
        for (AnnouncementSubscriptionStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown AnnouncementSubscriptionStatus: " + value);
    }
}
