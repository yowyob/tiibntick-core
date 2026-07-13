package com.yowyob.tiibntick.core.gofp.domain.model.enums;

public enum RelayDepositStatus {
    DEPOSITED,
    PENDING_RETRIEVAL,
    RETRIEVED,
    LOST,
    DAMAGED;

    public static RelayDepositStatus fromValue(String value) {
        for (RelayDepositStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown RelayDepositStatus: " + value);
    }
}
