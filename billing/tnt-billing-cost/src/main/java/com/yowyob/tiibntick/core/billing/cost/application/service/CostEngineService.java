package com.yowyob.tiibntick.core.billing.cost.application.service;

import com.yowyob.tiibntick.core.billing.cost.application.port.in.ICostUseCase;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeCostCommand;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeEquipmentCostCommand;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.ICostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.IFleetCostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.application.port.out.IRouteDataPort;
import com.yowyob.tiibntick.core.billing.cost.domain.enums.*;
import com.yowyob.tiibntick.core.billing.cost.domain.model.*;
import com.yowyob.tiibntick.core.billing.cost.domain.service.CostComputationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * CostEngineService — application-layer orchestrator for operational cost computation.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Fetches global CostParameters from tnt-settings-core.</li>
 *   <li>Optionally loads fleet-specific FleetCostParameters () from persistence.</li>
 *   <li>Enriches context with road type and weather data from tnt-route-core / tnt-geo-core.</li>
 *   <li>Delegates pure cost computation to CostComputationDomainService.</li>
 *   <li>Returns the immutable OperationalCost.</li>
 * </ol>
 *
 * <p> — Added:
 * <ul>
 *   <li>Fleet parameter resolution: if {@code ComputeCostCommand.ownerOrgId} is provided
 *       and FleetCostParameters is not pre-loaded in the command, fetches from persistence.</li>
 *   <li>{@link #computeEquipmentCost(ComputeEquipmentCostCommand)} — equipment costs.</li>
 *   <li>{@link #getFleetCostParameters(String)} / {@link #saveFleetCostParameters(FleetCostParameters)}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostEngineService implements ICostUseCase {

    private final ICostParametersPort costParametersPort;
    private final IRouteDataPort routeDataPort;
    private final IFleetCostParametersPort fleetCostParametersPort;

    @Override
    public Mono<OperationalCost> computeOperationalCost(ComputeCostCommand command) {
        log.debug("Computing operational cost for missionId={} tenantId={} ownerOrgId={}",
                command.missionId(), command.tenantId(), command.ownerOrgId());

        // Resolve fleet parameters: prefer command-embedded, fall back to persistence lookup
        Mono<FleetCostParameters> fleetParamsMono = resolveFleetParams(command);

        return Mono.zip(
                        costParametersPort.getForTenant(command.tenantId()),
                        enrichRoadType(command),
                        enrichWeatherCondition(command),
                        fleetParamsMono.map(fp -> (Object) fp).defaultIfEmpty(Boolean.FALSE))
                .flatMap(tuple -> {
                    CostParameters params = tuple.getT1();
                    RoadType roadType = tuple.getT2();
                    WeatherCondition weather = tuple.getT3();
                    FleetCostParameters fleetParams = tuple.getT4() instanceof FleetCostParameters fp ? fp : null;

                    CostContext context = buildContext(command, roadType, weather, fleetParams);
                    OperationalCost cost = CostComputationDomainService.compute(context, params);

                    log.info("Cost computed: missionId={} total={} fleet={}",
                            command.missionId(), cost.total(),
                            fleetParams != null ? "custom[" + fleetParams.ownerOrgId() + "]" : "global");
                    return Mono.just(cost);
                });
    }

    /* @Override
    public Mono<OperationalCost> computeWithParameters(ComputeCostCommand command,
                                                        CostParameters parameters) {
        // Resolve fleet params synchronously from command if available
        FleetCostParameters fleetParams = command.fleetCostParameters();

        return resolveFleetParams(command)
                .defaultIfEmpty(fleetParams != null ? fleetParams : new FleetCostParametersPlaceholder())
                .map(fp -> {
                    FleetCostParameters resolvedFleet = fp instanceof FleetCostParametersPlaceholder ? fleetParams : fp;
                    CostContext context = buildContext(command,
                            command.roadType() != null ? command.roadType() : RoadType.URBAN_PAVED,
                            command.weatherCondition() != null ? command.weatherCondition() : WeatherCondition.CLEAR,
                            resolvedFleet);
                    return CostComputationDomainService.compute(context, parameters);
                });
    } */

    @Override
    public Mono<OperationalCost> computeWithParameters(ComputeCostCommand command,
                                                        CostParameters parameters) {
        // Resolve fleet params: prefer command-embedded, then persistence, then null
        Mono<FleetCostParameters> fleetParamsMono = resolveFleetParams(command)
                .defaultIfEmpty(command.fleetCostParameters());

        return fleetParamsMono
                .map(fp -> {
                    CostContext context = buildContext(command,
                            command.roadType() != null ? command.roadType() : RoadType.URBAN_PAVED,
                            command.weatherCondition() != null ? command.weatherCondition() : WeatherCondition.CLEAR,
                            fp);
                    return CostComputationDomainService.compute(context, parameters);
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    // If no fleet params found, use null
                    CostContext context = buildContext(command,
                            command.roadType() != null ? command.roadType() : RoadType.URBAN_PAVED,
                            command.weatherCondition() != null ? command.weatherCondition() : WeatherCondition.CLEAR,
                            null);
                    return CostComputationDomainService.compute(context, parameters);
                }));
    }

    @Override
    public Mono<CostParameters> getCostParameters(UUID tenantId) {
        return costParametersPort.getForTenant(tenantId);
    }

    @Override
    public Mono<EquipmentCostResult> computeEquipmentCost(ComputeEquipmentCostCommand command) {
        log.debug("Computing equipment cost for missionId={} equipments={}",
                command.missionId(), command.equipmentTypes());
        if (command.equipmentTypes() == null || command.equipmentTypes().isEmpty()) {
            return Mono.just(EquipmentCostResult.zero());
        }
        return Mono.just(EquipmentCostResult.compute(command.equipmentTypes(), command.distanceKm()))
                .doOnNext(r -> log.debug("Equipment cost: {} XAF for {} types",
                        r.totalCostXaf(), r.equipmentCount()));
    }

    @Override
    public Mono<FleetCostParameters> getFleetCostParameters(String ownerOrgId) {
        return fleetCostParametersPort.findByOwnerOrgId(ownerOrgId);
    }

    @Override
    public Mono<FleetCostParameters> saveFleetCostParameters(FleetCostParameters params) {
        return fleetCostParametersPort.save(params);
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private Mono<FleetCostParameters> resolveFleetParams(ComputeCostCommand command) {
        // Priority: command-embedded > persistence lookup > empty
        if (command.fleetCostParameters() != null) {
            return Mono.just(command.fleetCostParameters());
        }
        if (command.ownerOrgId() != null && !command.ownerOrgId().isBlank()) {
            return fleetCostParametersPort.findByOwnerOrgId(command.ownerOrgId())
                    .doOnNext(fp -> log.debug("Loaded fleet params for org={}", command.ownerOrgId()))
                    .onErrorResume(e -> {
                        log.warn("Failed to load fleet params for org={}: {}", command.ownerOrgId(), e.getMessage());
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    private Mono<RoadType> enrichRoadType(ComputeCostCommand command) {
        if (command.roadType() != null && command.roadType() != RoadType.UNKNOWN) {
            return Mono.just(command.roadType());
        }
        if (command.missionId() == null) return Mono.just(RoadType.URBAN_PAVED);
        return routeDataPort.getDominantRoadType(command.missionId())
                .onErrorReturn(RoadType.URBAN_PAVED);
    }

    private Mono<WeatherCondition> enrichWeatherCondition(ComputeCostCommand command) {
        if (command.weatherCondition() != null && command.weatherCondition() != WeatherCondition.UNKNOWN) {
            return Mono.just(command.weatherCondition());
        }
        if (command.missionId() == null) return Mono.just(WeatherCondition.CLEAR);
        return routeDataPort.getWeatherCondition(command.missionId(), command.tenantId())
                .onErrorReturn(WeatherCondition.CLEAR);
    }

    private CostContext buildContext(ComputeCostCommand command,
                                     RoadType roadType, WeatherCondition weather,
                                     FleetCostParameters fleetParams) {
        return CostContext.builder()
                .missionId(command.missionId())
                .tenantId(command.tenantId())
                .distanceKm(command.distanceKm())
                .estimatedDurationMin(command.estimatedDurationMin())
                .roadType(roadType)
                .weatherCondition(weather)
                .vehicleType(command.vehicleType() != null ? command.vehicleType() : VehicleType.MOTORCYCLE)
                .priority(command.priority() != null ? command.priority() : MissionPriority.NORMAL)
                .payloadWeightKg(command.payloadWeightKg())
                .vehicleCapacityKg(command.vehicleCapacityKg() > 0
                        ? command.vehicleCapacityKg()
                        : (command.vehicleType() != null ? command.vehicleType().defaultCapacityKg() : 50.0))
                .fleetCostParameters(fleetParams)
                .activeEquipmentTypes(command.activeEquipmentTypes())
                .ownerOrgId(command.ownerOrgId())
                .vehicleFuelConsumptionL100km(command.vehicleFuelConsumptionL100km())
                .build();
    }

    /** Internal placeholder to handle Mono.zip not supporting null elements. */
    //private static final class FleetCostParametersPlaceholder extends Object {}
}
