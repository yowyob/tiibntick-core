package com.yowyob.tiibntick.core.media.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a unique identifier for a {@link MediaFile} aggregate.
 * Wraps a {@link UUID} to provide type safety across the domain.
 *
 * @author MANFOUO Braun
 */
public final class MediaFileId {

    private final UUID value;

    private MediaFileId(UUID value) {
        this.value = Objects.requireNonNull(value, "MediaFileId value must not be null");
    }

    @JsonCreator
    public static MediaFileId of(UUID value) {
        return new MediaFileId(value);
    }

    public static MediaFileId of(String value) {
        return new MediaFileId(UUID.fromString(value));
    }

    public static MediaFileId generate() {
        return new MediaFileId(UUID.randomUUID());
    }

    @JsonValue
    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaFileId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
