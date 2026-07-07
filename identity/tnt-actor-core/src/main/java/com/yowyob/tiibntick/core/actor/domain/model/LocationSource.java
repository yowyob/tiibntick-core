package com.yowyob.tiibntick.core.actor.domain.model;

public enum LocationSource {

    GPS,
    NETWORK,
    DECLARED,
    LAST_KNOWN;

    public static LocationSource from(String value) {
        if (value == null || value.isBlank()) {
            return DECLARED;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
