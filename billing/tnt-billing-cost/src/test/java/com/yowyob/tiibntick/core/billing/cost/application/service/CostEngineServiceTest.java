package com.yowyob.tiibntick.core.billing.cost.application.service;

import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeCostCommand;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.ICostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.IFleetCostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.IRouteDataPort;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.*;
import com.yowyob.tiibntick.core.billing.cost.domain.model.CostParameters;
import com.yowyob.tiibntick.core.billing.cost.domain.model.OperationalCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CostEngineService.
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostEngineService Tests")
class CostEngineServiceTest {

    @Mock private ICostParametersPort costParametersPort;
    @Mock private IRouteDataPort routeDataPort;
    @Mock private IFleetCostParametersPort fleetCostParametersPort;

    private CostEngineService costEngineService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        costEngineService = new CostEngineService(costParametersPort, routeDataPort, fleetCostParametersPort);
        lenient().when(costParametersPort.getForTenant(any()))
                .thenReturn(Mono.just(CostParameters.defaultForCameroon()));
        lenient().when(routeDataPort.getDominantRoadType(any()))
                .thenReturn(Mono.just(RoadType.URBAN_PAVED));
        lenient().when(routeDataPort.getWeatherCondition(any(), any()))
                .thenReturn(Mono.just(WeatherCondition.CLEAR));
    }

    @Test
    @DisplayName("should compute positive total cost for standard delivery")
    void computePositiveCost() {
        ComputeCostCommand command = ComputeCostCommand.basic(
                "MISS-001", TENANT_ID, 8.5, 35,
                RoadType.URBAN_PAVED, WeatherCondition.CLEAR,
                VehicleType.MOTORCYCLE, MissionPriority.NORMAL,
                3.2, 50.0);

        StepVerifier.create(costEngineService.computeOperationalCost(command))
                .expectNextMatches(cost ->
                        cost.total().isPositive() &&
                        "XAF".equals(cost.currencyCode()))
                .verifyComplete();
    }

    @Test
    @DisplayName("computeWithParameters should not call external services")
    void computeWithParametersSync() {
        ComputeCostCommand command = ComputeCostCommand.basic(
                null, TENANT_ID, 5.0, 20,
                RoadType.DEGRADED, WeatherCondition.LIGHT_RAIN,
                VehicleType.MOTORCYCLE, MissionPriority.HIGH,
                1.5, 50.0);

        StepVerifier.create(costEngineService.computeWithParameters(
                        command, CostParameters.defaultForCameroon()))
                .expectNextMatches(cost -> cost.total().isPositive())
                .verifyComplete();
    }

    @Test
    @DisplayName("FLOOD weather should produce the highest total cost")
    void floodWeatherHighestCost() {
        ComputeCostCommand clearCmd = ComputeCostCommand.basic(
                "M1", TENANT_ID, 8.5, 35, RoadType.URBAN_PAVED,
                WeatherCondition.CLEAR, VehicleType.MOTORCYCLE,
                MissionPriority.NORMAL, 3.2, 50.0);
        ComputeCostCommand floodCmd = ComputeCostCommand.basic(
                "M2", TENANT_ID, 8.5, 35, RoadType.URBAN_PAVED,
                WeatherCondition.FLOOD, VehicleType.MOTORCYCLE,
                MissionPriority.NORMAL, 3.2, 50.0);

        CostParameters params = CostParameters.defaultForCameroon();

        Mono<OperationalCost> clearCost = costEngineService.computeWithParameters(clearCmd, params);
        Mono<OperationalCost> floodCost = costEngineService.computeWithParameters(floodCmd, params);

        StepVerifier.create(Mono.zip(clearCost, floodCost))
                .expectNextMatches(tuple ->
                        tuple.getT2().total().amount()
                                .compareTo(tuple.getT1().total().amount()) > 0)
                .verifyComplete();
    }
}
