package com.yowyob.tiibntick.core.sync.domain.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class VectorClock {

    private final Map<String, Long> clocks;

    private VectorClock(Map<String, Long> clocks) {
        this.clocks = Collections.unmodifiableMap(new HashMap<>(clocks));
    }

    public static VectorClock empty() {
        return new VectorClock(new HashMap<>());
    }

    public static VectorClock of(Map<String, Long> clocks) {
        return new VectorClock(Objects.requireNonNull(clocks));
    }

    public VectorClock increment(String nodeId) {
        Map<String, Long> next = new HashMap<>(clocks);
        next.merge(nodeId, 1L, Long::sum);
        return new VectorClock(next);
    }

    public VectorClock merge(VectorClock other) {
        Map<String, Long> merged = new HashMap<>(clocks);
        other.clocks.forEach((k, v) -> merged.merge(k, v, Long::max));
        return new VectorClock(merged);
    }

    public CausalRelation compareWith(VectorClock other) {
        boolean thisDominates = false;
        boolean otherDominates = false;

        for (String key : allKeys(other)) {
            long thisVal = clocks.getOrDefault(key, 0L);
            long otherVal = other.clocks.getOrDefault(key, 0L);
            if (thisVal > otherVal) thisDominates = true;
            if (otherVal > thisVal) otherDominates = true;
        }

        if (thisDominates && !otherDominates) return CausalRelation.HAPPENS_BEFORE;
        if (otherDominates && !thisDominates) return CausalRelation.HAPPENS_AFTER;
        if (!thisDominates && !otherDominates) return CausalRelation.CONCURRENT_EQUAL;
        return CausalRelation.CONCURRENT;
    }

    private java.util.Set<String> allKeys(VectorClock other) {
        java.util.Set<String> keys = new java.util.HashSet<>(clocks.keySet());
        keys.addAll(other.clocks.keySet());
        return keys;
    }

    public Map<String, Long> asMap() {
        return clocks;
    }

    public enum CausalRelation {
        HAPPENS_BEFORE,
        HAPPENS_AFTER,
        CONCURRENT,
        CONCURRENT_EQUAL
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VectorClock vc)) return false;
        return Objects.equals(clocks, vc.clocks);
    }

    @Override
    public int hashCode() { return Objects.hash(clocks); }

    @Override
    public String toString() { return "VectorClock" + clocks; }
}
