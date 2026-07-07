package com.yowyob.tiibntick.core.geo.domain.exception;

/**
 * Thrown when coordinate validation fails (out-of-range lat/lng).
 *
 * Author: MANFOUO Braun
 */
public class InvalidCoordinatesException extends RuntimeException {

    public InvalidCoordinatesException(double lat, double lng) {
        super("Invalid coordinates: lat=" + lat + ", lng=" + lng);
    }

    public InvalidCoordinatesException(String message) {
        super(message);
    }
}
