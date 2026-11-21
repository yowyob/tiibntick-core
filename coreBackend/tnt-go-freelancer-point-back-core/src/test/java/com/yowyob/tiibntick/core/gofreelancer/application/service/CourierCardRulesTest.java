package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.yowyob.tiibntick.core.gofreelancer.application.service.CourierCardService.badge;
import static com.yowyob.tiibntick.core.gofreelancer.application.service.CourierCardService.loyaltyScore;
import static org.assertj.core.api.Assertions.assertThat;

/** Lot C-54 — badge thresholds, bounded loyalty score, vehicle vocabulary and platform grids. */
@DisplayName("C-54 — courier card rules")
class CourierCardRulesTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    final GofpCourierPricingProperties.Loyalty l = new GofpCourierPricingProperties.Loyalty();

    @Test
    @DisplayName("NEW < 5 delivered; CONFIRMED ≥ 5 (no gap, lot C-54b); LOYAL ≥ 50 and ≥ 6 months")
    void badges() {
        Instant old = NOW.minus(200, ChronoUnit.DAYS);
        Instant young = NOW.minus(100, ChronoUnit.DAYS);
        assertThat(badge(0, old, l, NOW)).isEqualTo("NEW");
        assertThat(badge(4, old, l, NOW)).isEqualTo("NEW");
        assertThat(badge(5, old, l, NOW)).isEqualTo("CONFIRMED");
        assertThat(badge(19, old, l, NOW)).isEqualTo("CONFIRMED");
        assertThat(badge(20, old, l, NOW)).isEqualTo("CONFIRMED");
        assertThat(badge(50, young, l, NOW)).as("50 delivered but 100 days old").isEqualTo("CONFIRMED");
        assertThat(badge(50, old, l, NOW)).isEqualTo("LOYAL");
        assertThat(badge(50, null, l, NOW)).as("unknown sign-up date is never LOYAL").isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("loyalty score is bounded in [0, 1]: half volume, half tenure")
    void scoreBounded() {
        assertThat(loyaltyScore(0, null, l, NOW)).isZero();
        assertThat(loyaltyScore(25, NOW.minus(365 / 2, ChronoUnit.DAYS), l, NOW)).isBetween(0.49, 0.51);
        assertThat(loyaltyScore(10_000, NOW.minus(10_000, ChronoUnit.DAYS), l, NOW)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TOPSIS weights sum to 1, loyalty stays below distance and rating")
    void weights() {
        double sum = com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisWeightsProbe.sum();
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisWeightsProbe.loyalty())
                .isLessThan(com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisWeightsProbe.rating());
    }

    @Test
    @DisplayName("one correspondence: moto→MOTORBIKE, tricycle→TRICYCLE, taxi→CAR, camion→TRUCK, bus→BUS")
    void vocabulary() {
        assertThat(VehicleCategory.canonicalName("moto")).isEqualTo("MOTORBIKE");
        assertThat(VehicleCategory.canonicalName("tricycle")).isEqualTo("TRICYCLE");
        assertThat(VehicleCategory.canonicalName("taxi")).isEqualTo("CAR");
        assertThat(VehicleCategory.canonicalName("camion")).isEqualTo("TRUCK");
        assertThat(VehicleCategory.canonicalName("bus")).isEqualTo("BUS");
        assertThat(VehicleCategory.parse("VAN")).isEqualTo(VehicleCategory.CAMION);
        assertThat(VehicleCategory.parse("SCOOTER")).isEqualTo(VehicleCategory.MOTO);
        assertThat(VehicleCategory.parse("avion")).isNull();
        assertThat(DeliveryNeedApplicationService.requiredVehicleOf("Peu importe")).isNull();
        assertThat(DeliveryNeedApplicationService.requiredVehicleOf("ANY")).isNull();
        assertThat(DeliveryNeedApplicationService.requiredVehicleOf("taxi")).isEqualTo("CAR");
    }

    @Test
    @DisplayName("platform grids per vehicle (defaults = application.yml); unknown vehicle → moto")
    void grids() {
        GofpCourierPricingProperties p = new GofpCourierPricingProperties();
        assertThat(p.gridFor(VehicleCategory.MOTO).getBaseFee()).isEqualTo(500.0);
        assertThat(p.gridFor(VehicleCategory.TRICYCLE).getPricePerKm()).isEqualTo(150.0);
        assertThat(p.gridFor(VehicleCategory.TAXI).getPricePerKg()).isEqualTo(30.0);
        assertThat(p.gridFor(VehicleCategory.CAMION).getBaseFee()).isEqualTo(3000.0);
        assertThat(p.gridFor(VehicleCategory.BUS).getPricePerKm()).isEqualTo(300.0);
        assertThat(p.gridFor(null).getBaseFee()).isEqualTo(500.0);
        assertThat(p.gridFor(VehicleCategory.BUS).getPricePerCbm()).isEqualTo(2000.0);
        assertThat(p.gridFor(VehicleCategory.BUS).getFragileSurcharge()).isEqualTo(500.0);
    }
}
