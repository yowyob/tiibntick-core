package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@code GeoCoordinates} value object and Haversine distance.
 *
 * @author MANFOUO Braun
 */
class GeoCoordinatesTest {

    // Yaoundé city centre approximate coordinates
    private static final GeoCoordinates YAOUNDE_CENTER  = new GeoCoordinates(3.8667, 11.5167);
    // Yaoundé Bastos quarter
    private static final GeoCoordinates YAOUNDE_BASTOS  = new GeoCoordinates(3.8800, 11.5250);

    @Test
    @DisplayName("Haversine distance between same point should be zero")
    void distanceToSelfShouldBeZero() {
        double dist = YAOUNDE_CENTER.haversineDistanceTo(YAOUNDE_CENTER);
        assertThat(dist).isLessThan(0.001);
    }

    @Test
    @DisplayName("Haversine between Yaoundé centre and Bastos should be ~2 km")
    void distanceBetweenYaoundePointsShouldBeReasonable() {
        double dist = YAOUNDE_CENTER.haversineDistanceTo(YAOUNDE_BASTOS);
        assertThat(dist).isBetween(1.0, 4.0);
    }

    @Test
    @DisplayName("Haversine distance should be symmetric")
    void distanceShouldBeSymmetric() {
        double d1 = YAOUNDE_CENTER.haversineDistanceTo(YAOUNDE_BASTOS);
        double d2 = YAOUNDE_BASTOS.haversineDistanceTo(YAOUNDE_CENTER);
        assertThat(d1).isCloseTo(d2, within(0.0001));
    }

    @Test
    @DisplayName("Invalid latitude should throw DeliveryDomainException")
    void invalidLatitudeShouldThrow() {
        assertThatThrownBy(() -> new GeoCoordinates(95.0, 11.5))
                .isInstanceOf(DeliveryDomainException.class)
                .hasMessageContaining("Latitude");
    }

    @Test
    @DisplayName("Invalid longitude should throw DeliveryDomainException")
    void invalidLongitudeShouldThrow() {
        assertThatThrownBy(() -> new GeoCoordinates(3.8, 200.0))
                .isInstanceOf(DeliveryDomainException.class)
                .hasMessageContaining("Longitude");
    }
}
