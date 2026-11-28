package com.yowyob.tiibntick.core.administration.domain.model.thresholds;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable set of threshold values keyed by {@link DeliveryThreshold}. A missing key means « not set » (for an
 * organization override: inherit the platform value).
 */
public final class DeliveryThresholdValues {

    private static final DeliveryThresholdValues EMPTY = new DeliveryThresholdValues(Map.of());

    private final Map<DeliveryThreshold, Integer> values;

    private DeliveryThresholdValues(Map<DeliveryThreshold, Integer> values) {
        EnumMap<DeliveryThreshold, Integer> copy = new EnumMap<>(DeliveryThreshold.class);
        values.forEach((k, v) -> {
            if (k != null && v != null) {
                copy.put(k, v);
            }
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    /** Values from a map; null keys or values are dropped (= not set). */
    public static DeliveryThresholdValues of(Map<DeliveryThreshold, Integer> values) {
        return values == null || values.isEmpty() ? EMPTY : new DeliveryThresholdValues(values);
    }

    public static DeliveryThresholdValues empty() {
        return EMPTY;
    }

    /** Every threshold at its decided default value. */
    public static DeliveryThresholdValues defaults() {
        EnumMap<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThreshold.class);
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            map.put(t, t.defaultValue());
        }
        return new DeliveryThresholdValues(map);
    }

    /** The value, or {@code null} when not set. */
    public Integer get(DeliveryThreshold threshold) {
        return values.get(threshold);
    }

    public boolean isSet(DeliveryThreshold threshold) {
        return values.containsKey(threshold);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean isComplete() {
        return values.size() == DeliveryThreshold.values().length;
    }

    public Map<DeliveryThreshold, Integer> asMap() {
        return values;
    }

    /** {@code field -> value} for every threshold, {@code null} when not set (event payloads, API). */
    public Map<String, Object> toFieldMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            map.put(t.field(), values.get(t));
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DeliveryThresholdValues other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "DeliveryThresholdValues" + values;
    }
}
