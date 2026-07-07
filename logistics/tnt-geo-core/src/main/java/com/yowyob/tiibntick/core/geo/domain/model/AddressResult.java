package com.yowyob.tiibntick.core.geo.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.Objects;

/**
 * Geocoding result returned by the Nominatim adapter.
 *
 * Author: MANFOUO Braun
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class AddressResult {

    private final String rawInput;
    private final GeoPoint coordinates;
    private final String displayName;
    private final String cityCode;
    private final double confidence;

    private AddressResult(String rawInput, GeoPoint coordinates, String displayName,
                          String cityCode, double confidence) {
        this.rawInput = Objects.requireNonNull(rawInput, "rawInput must not be null");
        this.coordinates = Objects.requireNonNull(coordinates, "coordinates must not be null");
        this.displayName = displayName != null ? displayName : rawInput;
        this.cityCode = cityCode != null ? cityCode.toUpperCase() : "UNKNOWN";
        if (confidence < 0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1], got: " + confidence);
        }
        this.confidence = confidence;
    }

    public static AddressResult of(String rawInput, GeoPoint coordinates, String displayName,
                                   String cityCode, double confidence) {
        return new AddressResult(rawInput, coordinates, displayName, cityCode, confidence);
    }

    public String rawInput()      { return rawInput; }
    public GeoPoint coordinates() { return coordinates; }
    public String displayName()   { return displayName; }
    public String cityCode()      { return cityCode; }
    public double confidence()    { return confidence; }

    @Override
    public String toString() {
        return "AddressResult{input='" + rawInput + "', coords=" + coordinates
                + ", confidence=" + confidence + "}";
    }
}
