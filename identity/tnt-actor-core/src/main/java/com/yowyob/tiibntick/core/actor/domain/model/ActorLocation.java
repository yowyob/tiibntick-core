package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class ActorLocation {

    private final double latitude;
    private final double longitude;
    private final Double accuracy;
    private final Instant timestamp;
    private final LocationSource source;

    private ActorLocation(double latitude, double longitude, Double accuracy,
                          Instant timestamp, LocationSource source) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    public static ActorLocation of(double latitude, double longitude, Double accuracy,
                                   Instant timestamp, LocationSource source) {
        return new ActorLocation(latitude, longitude, accuracy, timestamp, source);
    }

    public static ActorLocation gps(double latitude, double longitude, double accuracy) {
        return new ActorLocation(latitude, longitude, accuracy, Instant.now(), LocationSource.GPS);
    }

    public static ActorLocation declared(double latitude, double longitude) {
        return new ActorLocation(latitude, longitude, null, Instant.now(), LocationSource.DECLARED);
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public Double accuracy() {
        return accuracy;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public LocationSource source() {
        return source;
    }

    public boolean isGpsSource() {
        return source == LocationSource.GPS;
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got: " + longitude);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorLocation other)) return false;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && Objects.equals(accuracy, other.accuracy)
                && timestamp.equals(other.timestamp)
                && source == other.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, accuracy, timestamp, source);
    }

    @Override
    public String toString() {
        return "ActorLocation{lat=" + latitude + ", lng=" + longitude + ", source=" + source + "}";
    }
}
