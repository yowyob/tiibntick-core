package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.policy.DeliveryCostPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@code DeliveryCostPolicy}.
 * Validates the multi-criteria cost formula and minimum fare enforcement.
 *
 * @author MANFOUO Braun
 */
class DeliveryCostPolicyTest {

    @Test
    @DisplayName("Simple cost for 5 km motorbike STANDARD should be positive and non-trivial")
    void shouldComputePositiveCostForMotorbike5km() {
        DeliveryCost cost = DeliveryCostPolicy.computeSimple(5.0, 20, LogisticsType.MOTORBIKE, DeliveryUrgency.STANDARD);

        assertThat(cost.total()).isGreaterThan(BigDecimal.ZERO);
        assertThat(cost.currency()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("EXPRESS delivery should cost more than STANDARD for same route")
    void expressShouldCostMoreThanStandard() {
        DeliveryCost standard = DeliveryCostPolicy.computeSimple(8.0, 30, LogisticsType.MOTORBIKE, DeliveryUrgency.STANDARD);
        DeliveryCost express  = DeliveryCostPolicy.computeSimple(8.0, 30, LogisticsType.MOTORBIKE, DeliveryUrgency.EXPRESS);

        assertThat(express.total()).isGreaterThan(standard.total());
    }

    @Test
    @DisplayName("TRUCK should cost more per km than BIKE")
    void truckShouldCostMoreThanBike() {
        DeliveryCost bike  = DeliveryCostPolicy.computeSimple(10.0, 40, LogisticsType.BIKE, DeliveryUrgency.STANDARD);
        DeliveryCost truck = DeliveryCostPolicy.computeSimple(10.0, 40, LogisticsType.TRUCK, DeliveryUrgency.STANDARD);

        assertThat(truck.total()).isGreaterThan(bike.total());
    }

    @Test
    @DisplayName("Minimum fare of 500 XAF should be enforced for very short distances")
    void shouldEnforceMinimumFare() {
        DeliveryCost cost = DeliveryCostPolicy.computeSimple(0.1, 1, LogisticsType.BIKE, DeliveryUrgency.STANDARD);

        assertThat(cost.total()).isGreaterThanOrEqualTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("Full cost with road penibility should be higher than no penibility")
    void roadPenibilityShouldIncreaseCost() {
        DeliveryCost lowPen  = DeliveryCostPolicy.compute(5.0, 20, 0.0, 0.0, LogisticsType.MOTORBIKE, DeliveryUrgency.STANDARD);
        DeliveryCost highPen = DeliveryCostPolicy.compute(5.0, 20, 1.0, 0.0, LogisticsType.MOTORBIKE, DeliveryUrgency.STANDARD);

        assertThat(highPen.total()).isGreaterThan(lowPen.total());
    }

    @Test
    @DisplayName("Cost components should all be non-negative")
    void allComponentsShouldBeNonNegative() {
        DeliveryCost cost = DeliveryCostPolicy.compute(7.0, 25, 0.3, 0.1, LogisticsType.CAR, DeliveryUrgency.SAME_DAY);

        assertThat(cost.distanceCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(cost.timeCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(cost.roadPenibilityCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(cost.weatherRiskCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(cost.fuelCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
