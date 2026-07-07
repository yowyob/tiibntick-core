package com.yowyob.tiibntick.core.geo.application.service;

import com.yowyob.tiibntick.core.geo.application.port.out.IGeoEventPublisher;
import com.yowyob.tiibntick.core.geo.application.port.out.IRoadArcRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IWeatherApiClient;
import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the 5D cost function computation.
 *
 * Author: MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class CostFunctionServiceTest {

    @Mock private IRoadArcRepository arcRepository;
    @Mock private IWeatherApiClient weatherClient;
    @Mock private IGeoEventPublisher eventPublisher;

    private CostFunctionService costFunctionService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        costFunctionService = new CostFunctionService(arcRepository, weatherClient, eventPublisher);
    }

    @Test
    void computeCost_clearWeather_pavedRoad_returnsLowCost() {
        RoadArc arc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                5.0, RoadType.PAVED, 50.0, 1.0, false,
                Instant.now(), Instant.now()
        );
        WeatherCondition clear = WeatherCondition.clear(Instant.now());
        CostWeights weights = CostWeights.defaultWeights();

        double cost = costFunctionService.computeCost(arc, weights, clear);

        // distance component: 5/200 = 0.025 * 0.25 = 0.00625
        // time component: (5/50)/4 = 0.025 * 0.35 = 0.00875
        // penibility: 0 * 0.10 = 0
        // weather: 0 * 0.15 = 0
        // fuel: (5*25)/5000 = 0.025 * 0.15 = 0.00375
        double expected = 0.025 * 0.25 + 0.025 * 0.35 + 0.0 * 0.10 + 0.0 * 0.15 + 0.025 * 0.15;
        assertThat(cost).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void computeCost_heavyRainDirtRoad_returnsHighCost() {
        RoadArc arc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                20.0, RoadType.DIRT, 20.0, 1.5, false,
                Instant.now(), Instant.now()
        );
        WeatherCondition heavyRain = WeatherCondition.of(15.0, 40.0, Instant.now());
        CostWeights weights = CostWeights.defaultWeights();

        double cost = costFunctionService.computeCost(arc, weights, heavyRain);

        // All components contribute positively with heavy rain + dirt road
        assertThat(cost).isGreaterThan(0.1);
    }

    @Test
    void computeCost_distancePriorityWeights_distanceComponentDominates() {
        RoadArc shortArc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                2.0, RoadType.PAVED, 60.0, 1.0, false,
                Instant.now(), Instant.now()
        );
        RoadArc longArc = RoadArc.rehydrate(
                RoadArcId.generate(), TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                20.0, RoadType.HIGHWAY, 100.0, 1.0, false,
                Instant.now(), Instant.now()
        );
        WeatherCondition clear = WeatherCondition.clear(Instant.now());
        CostWeights distWeights = CostWeights.distancePriority();

        double shortCost = costFunctionService.computeCost(shortArc, distWeights, clear);
        double longCost  = costFunctionService.computeCost(longArc, distWeights, clear);

        assertThat(shortCost).isLessThan(longCost);
    }

    @Test
    void computeCompositeCostWithWeather_arcNotFound_emitsError() {
        RoadArcId unknownId = RoadArcId.generate();
        when(arcRepository.findById(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(
                costFunctionService.computeCompositeCostWithWeather(
                        unknownId, CostWeights.defaultWeights(), WeatherCondition.clear(Instant.now()))
        )
        .expectErrorMatches(ex -> ex.getMessage().contains(unknownId.value()))
        .verify();
    }

    @Test
    void computeCompositeCostWithWeather_arcFound_emitsNonNegativeCost() {
        RoadArcId arcId = RoadArcId.generate();
        RoadArc arc = RoadArc.rehydrate(
                arcId, TENANT,
                RoadNodeId.generate(), RoadNodeId.generate(),
                8.0, RoadType.PAVED, 50.0, 1.0, false,
                Instant.now(), Instant.now()
        );
        when(arcRepository.findById(arcId, null)).thenReturn(Mono.just(arc));

        StepVerifier.create(
                costFunctionService.computeCompositeCostWithWeather(
                        arcId, CostWeights.defaultWeights(), WeatherCondition.clear(Instant.now()))
        )
        .assertNext(cost -> assertThat(cost).isGreaterThanOrEqualTo(0.0))
        .verifyComplete();
    }
}
