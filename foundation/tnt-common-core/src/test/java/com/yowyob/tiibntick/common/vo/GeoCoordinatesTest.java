package com.yowyob.tiibntick.common.vo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GeoCoordinates.
 * Author: MANFOUO Braun
 */
class GeoCoordinatesTest {

    private static final GeoCoordinates YAOUNDE = GeoCoordinates.of(3.8480, 11.5021);
    private static final GeoCoordinates DOUALA = GeoCoordinates.of(4.0483, 9.7043);

    @Test
    void should_compute_haversine_distance_yaounde_to_douala() {
        double distanceMeters = YAOUNDE.haversineDistanceTo(DOUALA);
        assertThat(distanceMeters).isBetween(200_000.0, 230_000.0);
    }

    @Test
    void should_reject_invalid_latitude() {
        assertThatThrownBy(() -> GeoCoordinates.of(91.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_parse_lat_lon_string() {
        GeoCoordinates parsed = GeoCoordinates.parse("3.8480,11.5021");
        assertThat(parsed).isEqualTo(YAOUNDE);
    }
}
