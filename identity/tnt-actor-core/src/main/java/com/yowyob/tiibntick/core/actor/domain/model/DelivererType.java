package com.yowyob.tiibntick.core.actor.domain.model;

public enum DelivererType {

    PERMANENT,
    FREELANCER_ASSOCIATED;

    public static DelivererType from(String value) {
        if (value == null || value.isBlank()) {
            return PERMANENT;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
