package com.yowyob.tiibntick.core.sync.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SyncSessionId(String value) {

    public SyncSessionId {
        Objects.requireNonNull(value);
        if (value.isBlank()) throw new IllegalArgumentException("SyncSessionId must not be blank");
    }

    public static SyncSessionId generate() {
        return new SyncSessionId(UUID.randomUUID().toString());
    }

    public static SyncSessionId of(String value) {
        return new SyncSessionId(value);
    }

    @Override
    public String toString() { return value; }
}
