package com.yowyob.tiibntick.core.sync.domain.model;

import java.util.Objects;
import java.util.UUID;

public record OfflineOpId(String value) {

    public OfflineOpId {
        Objects.requireNonNull(value);
        if (value.isBlank()) throw new IllegalArgumentException("OfflineOpId must not be blank");
    }

    public static OfflineOpId generate() {
        return new OfflineOpId(UUID.randomUUID().toString());
    }

    public static OfflineOpId of(String value) {
        return new OfflineOpId(value);
    }

    @Override
    public String toString() { return value; }
}
