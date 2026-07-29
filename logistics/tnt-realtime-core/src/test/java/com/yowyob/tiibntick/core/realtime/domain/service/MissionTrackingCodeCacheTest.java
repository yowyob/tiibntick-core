package com.yowyob.tiibntick.core.realtime.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MissionTrackingCodeCache}.
 *
 * @author MANFOUO Braun
 */
class MissionTrackingCodeCacheTest {

    private final MissionTrackingCodeCache cache = new MissionTrackingCodeCache();

    @Test
    @DisplayName("put() then get() returns the cached tracking code")
    void putThenGetReturnsCode() {
        cache.put("M1", "TNT-20260725-A1B2C3D4");
        assertThat(cache.get("M1")).isEqualTo("TNT-20260725-A1B2C3D4");
    }

    @Test
    @DisplayName("get() for an unknown mission returns null")
    void getUnknownMissionReturnsNull() {
        assertThat(cache.get("unknown")).isNull();
    }

    @Test
    @DisplayName("put() with a null or blank trackingCode is a no-op")
    void putWithBlankTrackingCodeIsNoop() {
        cache.put("M2", null);
        cache.put("M2", "");
        assertThat(cache.get("M2")).isNull();
    }

    @Test
    @DisplayName("get() with a null missionId returns null instead of throwing")
    void getWithNullMissionIdReturnsNull() {
        assertThat(cache.get(null)).isNull();
    }
}
