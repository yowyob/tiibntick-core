package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.route.domain.model.ReroutingChoice;
import com.yowyob.tiibntick.core.route.domain.model.ReroutingDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ReroutingServiceTest {

    @Test
    void evaluateRerouting_significantImprovement_decides_REROUTE() {
        ReroutingDecision d = ReroutingDecision.evaluate("M1",
                1.0, 0.5, 1.0, 0.05);
        assertThat(d.shouldReroute()).isTrue();
        assertThat(d.decision()).isEqualTo(ReroutingChoice.REROUTE);
    }

    @Test
    void evaluateRerouting_marginalImprovement_decides_KEEP() {
        ReroutingDecision d = ReroutingDecision.evaluate("M1",
                1.0, 0.95, 1.0, 0.05);
        assertThat(d.shouldReroute()).isFalse();
        assertThat(d.decision()).isEqualTo(ReroutingChoice.KEEP_CURRENT);
    }

    @Test
    void evaluateRerouting_exactlyAtThreshold_decides_KEEP() {
        double epsilon = 0.15 * 1.0;
        double switchCost = 0.05;
        ReroutingDecision d = ReroutingDecision.evaluate("M1",
                1.0, 1.0 - epsilon - switchCost, 1.0, switchCost);
        assertThat(d.shouldReroute()).isFalse();
    }

    @Test
    void costImprovement_calculatedCorrectly() {
        ReroutingDecision d = ReroutingDecision.evaluate("M1",
                1.0, 0.6, 1.0, 0.05);
        assertThat(d.costImprovement()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
    }
}
