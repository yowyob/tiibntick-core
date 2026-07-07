package com.yowyob.tiibntick.core.billing.cost.domain;

import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FleetCostParameters}.
 *
 * @author MANFOUO Braun
 */
class FleetCostParametersTest {

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Should reject zero or negative fuel price")
        void shouldRejectZeroFuelPrice() {
            assertThatThrownBy(() -> FleetCostParameters.builder()
                    .ownerOrgId("FRL-001")
                    .fuelPriceLiterXAF(BigDecimal.ZERO)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fuelPriceLiterXAF must be positive");
        }

        @Test
        @DisplayName("Should reject terrain factor below 1.0")
        void shouldRejectTerrainFactorBelow1() {
            assertThatThrownBy(() -> FleetCostParameters.builder()
                    .ownerOrgId("FRL-001")
                    .fuelPriceLiterXAF(new BigDecimal("700"))
                    .terrainDegradationFactor(new BigDecimal("0.5"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("terrainDegradationFactor");
        }

        @Test
        @DisplayName("Should reject terrain factor above 2.0")
        void shouldRejectTerrainFactorAbove2() {
            assertThatThrownBy(() -> FleetCostParameters.builder()
                    .ownerOrgId("FRL-001")
                    .fuelPriceLiterXAF(new BigDecimal("700"))
                    .terrainDegradationFactor(new BigDecimal("3.0"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject rain penalty factor above 1.5")
        void shouldRejectRainFactorAbove1point5() {
            assertThatThrownBy(() -> FleetCostParameters.builder()
                    .ownerOrgId("FRL-001")
                    .fuelPriceLiterXAF(new BigDecimal("700"))
                    .rainPenaltyFactor(new BigDecimal("2.0"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Defaults and factories")
    class Defaults {

        @Test
        @DisplayName("defaultForMotoFreelancer should return valid parameters")
        void shouldCreateValidMotoDefaults() {
            FleetCostParameters params = FleetCostParameters.defaultForMotoFreelancer("FRL-001");
            assertThat(params.ownerOrgId()).isEqualTo("FRL-001");
            assertThat(params.fuelPriceLiterXAF()).isEqualByComparingTo("700");
            assertThat(params.vehicleWearRatePerKm()).isEqualByComparingTo("10");
            assertThat(params.timeValuePerHour()).isEqualByComparingTo("500");
            assertThat(params.terrainDegradationFactor()).isEqualByComparingTo("1.0");
            assertThat(params.rainPenaltyFactor()).isEqualByComparingTo("1.1");
        }

        @Test
        @DisplayName("timeValuePerMinute should divide by 60")
        void shouldComputeTimePerMinute() {
            FleetCostParameters params = FleetCostParameters.defaultForMotoFreelancer("FRL-001");
            BigDecimal perMin = params.timeValuePerMinute();
            assertThat(perMin).isNotNull();
            assertThat(perMin.multiply(new BigDecimal("60")).setScale(2, java.math.RoundingMode.HALF_UP))
                    .isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("effectiveTerrainFactor should return 1.0 when not set")
        void shouldDefaultTerrainFactorTo1() {
            FleetCostParameters params = FleetCostParameters.builder()
                    .ownerOrgId("FRL-001")
                    .fuelPriceLiterXAF(new BigDecimal("700"))
                    .build();
            assertThat(params.effectiveTerrainFactor()).isEqualTo(1.0);
        }
    }
}
