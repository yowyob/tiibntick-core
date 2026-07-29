package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint;

public enum RelayPointStatus {
    PENDING,
    APPROVED,
    SUSPENDED,
    REJECTED,
    REVOKED;

    public String getValue() { return name(); }
}
