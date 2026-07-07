package com.yowyob.tiibntick.core.billing.cost.adapter.in.web;

import com.yowyob.tiibntick.core.billing.cost.adapter.in.web.dto.request.ComputeCostRequest;
import com.yowyob.tiibntick.core.billing.cost.adapter.in.web.dto.response.OperationalCostResponse;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.ICostUseCase;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeCostCommand;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeEquipmentCostCommand;
import com.yowyob.tiibntick.core.billing.cost.domain.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * CostController — WebFlux REST controller for the billing cost engine.
 *
 * <p>Base path: {@code /billing/cost}
 *
 * <p> — Added endpoints:
 * <ul>
 *   <li>POST /billing/cost/equipment — equipment-specific cost computation.</li>
 *   <li>GET  /billing/cost/fleet-params/{ownerOrgId} — retrieve fleet parameters.</li>
 *   <li>PUT  /billing/cost/fleet-params — save/update fleet parameters.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestController
@RequestMapping("/billing/cost")
@RequiredArgsConstructor
@Tag(name = "Billing Cost Engine", description = "Operational cost computation for delivery missions")
public class CostController {

    private final ICostUseCase costUseCase;

    // ─── Core cost computation ─────────────────────────────────────────────

    @PostMapping("/compute")
    @Operation(summary = "Compute operational cost",
               description = "Computes the full operational cost for a delivery mission. "
                           + "Fetches road and weather data from external services if missionId is provided. "
                           + "Supports FreelancerOrg fleet parameters ().")
    public Mono<OperationalCostResponse> computeCost(@Valid @RequestBody ComputeCostRequest request) {
        log.info("POST /billing/cost/compute missionId={} dist={}km ownerOrgId={}",
                request.missionId(), request.distanceKm(), request.ownerOrgId());
        return costUseCase.computeOperationalCost(toCommand(request))
                .map(cost -> toResponse(request.missionId(), cost));
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview cost (synchronous)",
               description = "Computes cost synchronously without external service calls. "
                           + "Used for real-time pricing previews. Supports fleet parameters ().")
    public Mono<OperationalCostResponse> previewCost(@Valid @RequestBody ComputeCostRequest request) {
        log.debug("POST /billing/cost/preview dist={}km ownerOrgId={}", request.distanceKm(), request.ownerOrgId());
        ComputeCostCommand command = toCommand(request);
        return costUseCase.getCostParameters(request.tenantId())
                .flatMap(params -> costUseCase.computeWithParameters(command, params))
                .map(cost -> toResponse(request.missionId(), cost));
    }

    @GetMapping("/parameters")
    @Operation(summary = "Get global CostParameters for a tenant")
    public Mono<CostParameters> getCostParameters(@RequestParam UUID tenantId) {
        return costUseCase.getCostParameters(tenantId);
    }

    // ─── Equipment cost () ─────────────────────────────────────────────

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Compute equipment additional cost",
               description = "Computes the extra operational cost for special equipment "
                           + "(refrigerated box, cargo bag, tracker, etc.) deployed in a FreelancerOrg mission.")
    public Mono<EquipmentCostResult> computeEquipmentCost(
            @RequestParam UUID tenantId,
            @RequestParam Set<String> equipmentTypes,
            @RequestParam double distanceKm,
            @RequestParam(required = false) String missionId) {

        log.debug("POST /billing/cost/equipment types={} dist={}km", equipmentTypes, distanceKm);
        return costUseCase.computeEquipmentCost(
                new ComputeEquipmentCostCommand(tenantId, equipmentTypes, distanceKm, missionId));
    }

    // ─── Fleet cost parameters management () ──────────────────────────

    @GetMapping("/fleet-params/{ownerOrgId}")
    @Operation(summary = "Get FleetCostParameters for a FreelancerOrg or Agency",
               description = "Retrieves the fleet-specific cost calibration parameters for the given org.")
    public Mono<FleetCostParameters> getFleetCostParameters(@PathVariable String ownerOrgId) {
        log.debug("GET /billing/cost/fleet-params/{}", ownerOrgId);
        return costUseCase.getFleetCostParameters(ownerOrgId);
    }

    @PutMapping("/fleet-params")
    @Operation(summary = "Save or update FleetCostParameters",
               description = "Creates or updates the fleet cost parameters for a FreelancerOrg or Agency. "
                           + "These parameters override global CostParameters in cost computations.")
    public Mono<FleetCostParameters> saveFleetCostParameters(
            @Valid @RequestBody FleetCostParametersRequest request) {

        log.info("PUT /billing/cost/fleet-params ownerOrgId={}", request.ownerOrgId());
        FleetCostParameters params = FleetCostParameters.builder()
                .ownerOrgId(request.ownerOrgId())
                .fuelPriceLiterXAF(request.fuelPriceLiterXAF())
                .vehicleWearRatePerKm(request.vehicleWearRatePerKm())
                .timeValuePerHour(request.timeValuePerHour())
                .terrainDegradationFactor(request.terrainDegradationFactor())
                .rainPenaltyFactor(request.rainPenaltyFactor())
                .autoUpdateFuelPrice(request.autoUpdateFuelPrice())
                .build();
        return costUseCase.saveFleetCostParameters(params);
    }

    // ─── Request DTOs ──────────────────────────────────────────────────────

    /** Request DTO for saving fleet cost parameters. */
    public record FleetCostParametersRequest(
            String ownerOrgId,
            BigDecimal fuelPriceLiterXAF,
            BigDecimal vehicleWearRatePerKm,
            BigDecimal timeValuePerHour,
            BigDecimal terrainDegradationFactor,
            BigDecimal rainPenaltyFactor,
            Boolean autoUpdateFuelPrice
    ) {}

    // ─── Helpers ──────────────────────────────────────────────────────────

    private ComputeCostCommand toCommand(ComputeCostRequest request) {
        return new ComputeCostCommand(
                request.missionId(),
                request.tenantId(),
                request.distanceKm(),
                request.estimatedDurationMin() != null ? request.estimatedDurationMin() : 0,
                request.roadType(),
                request.weatherCondition(),
                request.vehicleType(),
                request.priority(),
                request.payloadWeightKg(),
                request.vehicleCapacityKg(),
                null,  // fleetCostParameters — resolved from ownerOrgId in service
                request.activeEquipmentTypes(),
                request.ownerOrgId(),
                request.vehicleFuelConsumptionL100km());
    }

    private OperationalCostResponse toResponse(String missionId, OperationalCost cost) {
        return new OperationalCostResponse(
                missionId,
                cost.fuelCost().amount(),
                cost.vehicleWearCost().amount(),
                cost.timeCost().amount(),
                cost.penibilityCost().amount(),
                cost.weatherSurcharge().amount(),
                cost.otherCosts().amount(),
                cost.total().amount(),
                cost.currencyCode(),
                cost.breakdownPercentages(),
                LocalDateTime.now());
    }
}
