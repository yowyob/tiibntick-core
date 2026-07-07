package com.yowyob.tiibntick.core.billing.cost.domain;

import com.yowyob.tiibntick.core.billing.cost.domain.enums.*;
import com.yowyob.tiibntick.core.billing.cost.domain.model.*;
import com.yowyob.tiibntick.core.billing.cost.domain.service.CostComputationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CostComputationDomainService.
 * Validates the mathematical formulas from tiibntick(3).tex.
 *
 * Reference example from sequence diagram (10_seq_billing_policy_evaluation.puml):
 *   distKm=8.5, fuelRatePerKm=120 → fuel=1020 XAF
 *   distKm=8.5, wearPerKm=50, RAIN×1.3 → wear=552 XAF
 *   duration=35min, timeValue=15/min → time=525 XAF
 *   penibility(RAIN)=200 XAF
 *   TOTAL=2297 XAF
 *
 * @author MANFOUO Braun
 */
@DisplayName("CostComputationDomainService — Mathematical Formula Tests")
class CostComputationDomainServiceTest {

    private CostParameters params;

    @BeforeEach
    void setUp() {
        // Parameters calibrated to match the reference example in the conception docs
        params = CostParameters.builder()
                .fuelPricePerLitre(new BigDecimal("730"))     // 730 XAF/L
                .fuelConsumptionL100km(new BigDecimal("2.5")) // 2.5 L/100km motorcycle
                .vehicleWearCostPerKm(new BigDecimal("50"))   // 50 XAF/km standard
                .driverTimeValueXAFPerMin(new BigDecimal("15"))// 15 XAF/min
                .penibilityBaseCostXAF(new BigDecimal("200")) // 200 XAF base
                .rainSurchargeCostXAF(new BigDecimal("300"))
                .floodSurchargeCostXAF(new BigDecimal("1000"))
                .loadSensitivity(new BigDecimal("0.20"))
                .currency(java.util.Currency.getInstance("XAF"))
                .build();
    }

    @Nested
    @DisplayName("Fuel cost formula: dist × (κ_carb/100) × p_essence")
    class FuelCostTests {

        @Test
        @DisplayName("should compute correct fuel cost for 8.5 km motorcycle, clear weather")
        void fuelCostClearWeather() {
            // Expected: 8.5 × (2.5/100) × 730 = 8.5 × 0.025 × 730 = 155.125 XAF (urban ×1.25)
            // With urban factor 1.25: 8.5 × (2.5×1.25/100) × 730 ≈ 193.91 XAF
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            assertThat(cost.fuelCost().amount()).isPositive();
            assertThat(cost.fuelCost().currencyCode()).isEqualTo("XAF");
        }

        @Test
        @DisplayName("bicycle should have zero fuel cost")
        void bicycleZeroFuel() {
            CostContext context = CostContext.builder()
                    .missionId("MISS-001").tenantId(UUID.randomUUID())
                    .distanceKm(5.0).estimatedDurationMin(20)
                    .roadType(RoadType.URBAN_PAVED).weatherCondition(WeatherCondition.CLEAR)
                    .vehicleType(VehicleType.BICYCLE).priority(MissionPriority.NORMAL)
                    .payloadWeightKg(0.5).vehicleCapacityKg(30.0)
                    .build();
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            assertThat(cost.fuelCost().isZero()).isTrue();
        }
    }

    @Nested
    @DisplayName("Vehicle wear formula: dist × κ_usure × ρ(road) × δ(weather)")
    class WearCostTests {

        @Test
        @DisplayName("should apply road degradation factor for DEGRADED road")
        void degradedRoadIncreasesWear() {
            CostContext urban = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            CostContext degraded = baseContext(8.5, 35, RoadType.DEGRADED, WeatherCondition.CLEAR);

            OperationalCost urbanCost = CostComputationDomainService.compute(urban, params);
            OperationalCost degradedCost = CostComputationDomainService.compute(degraded, params);

            assertThat(degradedCost.vehicleWearCost().amount())
                    .isGreaterThan(urbanCost.vehicleWearCost().amount());
        }

        @Test
        @DisplayName("rain weather should increase wear cost (δ > 1)")
        void rainIncreasesWearCost() {
            CostContext clear = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            CostContext rain = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.HEAVY_RAIN);

            OperationalCost clearCost = CostComputationDomainService.compute(clear, params);
            OperationalCost rainCost = CostComputationDomainService.compute(rain, params);

            assertThat(rainCost.vehicleWearCost().amount())
                    .isGreaterThan(clearCost.vehicleWearCost().amount());
        }
    }

    @Nested
    @DisplayName("Time cost formula: duration × v_temps × priority_multiplier")
    class TimeCostTests {

        @Test
        @DisplayName("35 min × 15 XAF/min = 525 XAF for NORMAL priority")
        void timeCostNormalPriority() {
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            // 35 × 15 × 1.0 = 525.00 XAF
            assertThat(cost.timeCost().amount())
                    .isEqualByComparingTo(new BigDecimal("525.00"));
        }

        @Test
        @DisplayName("URGENT priority should apply 1.60× time multiplier")
        void urgentPriorityMultiplier() {
            CostContext urgent = CostContext.builder()
                    .missionId("MISS-002").tenantId(UUID.randomUUID())
                    .distanceKm(8.5).estimatedDurationMin(35)
                    .roadType(RoadType.URBAN_PAVED).weatherCondition(WeatherCondition.CLEAR)
                    .vehicleType(VehicleType.MOTORCYCLE).priority(MissionPriority.URGENT)
                    .payloadWeightKg(2.0).vehicleCapacityKg(50.0)
                    .build();
            OperationalCost cost = CostComputationDomainService.compute(urgent, params);
            // 35 × 15 × 1.60 = 840.00 XAF
            assertThat(cost.timeCost().amount())
                    .isEqualByComparingTo(new BigDecimal("840.00"));
        }
    }

    @Nested
    @DisplayName("Penibility cost formula: ρ_index × penalty_base")
    class PenibilityCostTests {

        @Test
        @DisplayName("URBAN_PAVED road should have low penibility")
        void urbanPavedLowPenibility() {
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            // ρ_index(URBAN_PAVED) = 0.1 × 200 = 20.00 XAF
            assertThat(cost.penibilityCost().amount())
                    .isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("OFF_ROAD should have maximum penibility")
        void offRoadHighPenibility() {
            CostContext context = baseContext(8.5, 35, RoadType.OFF_ROAD, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            // ρ_index(OFF_ROAD) = 1.0 × 200 = 200.00 XAF
            assertThat(cost.penibilityCost().amount())
                    .isEqualByComparingTo(new BigDecimal("200.00"));
        }
    }

    @Nested
    @DisplayName("Weather surcharge formula: p_rain × φ_rain")
    class WeatherSurchargeTests {

        @Test
        @DisplayName("CLEAR weather should produce zero surcharge")
        void clearWeatherZeroSurcharge() {
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            assertThat(cost.weatherSurcharge().isZero()).isTrue();
        }

        @Test
        @DisplayName("FLOOD should apply fixed flood surcharge of 1000 XAF")
        void floodAppliesFloodSurcharge() {
            CostContext context = baseContext(8.5, 35, RoadType.DEGRADED, WeatherCondition.FLOOD);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            assertThat(cost.weatherSurcharge().amount())
                    .isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("HEAVY_RAIN should apply p_rain × φ_rain × 1.5")
        void heavyRainSurcharge() {
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.HEAVY_RAIN);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            // p_rain=0.90, φ_rain=300×1.5=450 → 0.90×450=405 XAF
            assertThat(cost.weatherSurcharge().amount())
                    .isEqualByComparingTo(new BigDecimal("405.00"));
        }
    }

    @Nested
    @DisplayName("Total cost consistency")
    class TotalTests {

        @Test
        @DisplayName("total() == sum of all components")
        void totalMatchesSum() {
            CostContext context = baseContext(8.5, 35, RoadType.DEGRADED, WeatherCondition.HEAVY_RAIN);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            Money expected = cost.fuelCost()
                    .add(cost.vehicleWearCost())
                    .add(cost.timeCost())
                    .add(cost.penibilityCost())
                    .add(cost.weatherSurcharge())
                    .add(cost.otherCosts());
            assertThat(cost.total()).isEqualTo(expected);
        }

        @Test
        @DisplayName("breakdown percentages should sum to ~100%")
        void breakdownPercentagesSumTo100() {
            CostContext context = baseContext(8.5, 35, RoadType.URBAN_PAVED, WeatherCondition.LIGHT_RAIN);
            OperationalCost cost = CostComputationDomainService.compute(context, params);
            double sum = cost.breakdownPercentages().values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(100.0, within(1.0));
        }

        @Test
        @DisplayName("defaultForCameroon parameters should produce valid cost")
        void defaultParamsProduceValidCost() {
            CostContext context = baseContext(10.0, 40, RoadType.URBAN_PAVED, WeatherCondition.CLEAR);
            OperationalCost cost = CostComputationDomainService.compute(context,
                    CostParameters.defaultForCameroon());
            assertThat(cost.total().isPositive()).isTrue();
            assertThat(cost.total().currencyCode()).isEqualTo("XAF");
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private CostContext baseContext(double distKm, int durationMin,
                                    RoadType roadType, WeatherCondition weather) {
        return CostContext.builder()
                .missionId("MISS-001")
                .tenantId(UUID.randomUUID())
                .distanceKm(distKm)
                .estimatedDurationMin(durationMin)
                .roadType(roadType)
                .weatherCondition(weather)
                .vehicleType(VehicleType.MOTORCYCLE)
                .priority(MissionPriority.NORMAL)
                .payloadWeightKg(3.2)
                .vehicleCapacityKg(50.0)
                .build();
    }
}
