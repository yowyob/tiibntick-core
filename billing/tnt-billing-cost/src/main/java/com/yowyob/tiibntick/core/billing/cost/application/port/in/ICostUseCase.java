package com.yowyob.tiibntick.core.billing.cost.application.port.in;

import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeCostCommand;
import com.yowyob.tiibntick.core.billing.cost.application.port.in.command.ComputeEquipmentCostCommand;
import com.yowyob.tiibntick.core.billing.cost.domain.model.CostParameters;
import com.yowyob.tiibntick.core.billing.cost.domain.model.EquipmentCostResult;
import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import com.yowyob.tiibntick.core.billing.cost.domain.model.OperationalCost;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ICostUseCase — primary port for operational cost computation.
 * Implemented by CostEngineService.
 *
 * <p> — Added:
 * <ul>
 *   <li>{@link #computeEquipmentCost(ComputeEquipmentCostCommand)} — equipment-specific cost.</li>
 *   <li>{@link #getFleetCostParameters(String)} — retrieve per-fleet parameters.</li>
 *   <li>{@link #saveFleetCostParameters(FleetCostParameters)} — persist fleet parameters.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface ICostUseCase {

    /**
     * Computes the full operational cost for a delivery mission.
     * Fetches road conditions and weather from tnt-route-core / tnt-geo-core.
     * If the command includes FleetCostParameters, they override global params.
     *
     * @param command all mission inputs
     * @return complete OperationalCost breakdown
     */
    Mono<OperationalCost> computeOperationalCost(ComputeCostCommand command);

    /**
     * Computes cost using explicitly provided context (no external API calls).
     * Used when route details are already available (e.g. billing preview).
     *
     * @param command     mission inputs
     * @param parameters  pre-loaded tenant parameters
     * @return complete OperationalCost breakdown
     */
    Mono<OperationalCost> computeWithParameters(ComputeCostCommand command, CostParameters parameters);

    /**
     * Returns the current calibrated CostParameters for a tenant.
     *
     * @param tenantId tenant identifier
     * @return current CostParameters
     */
    Mono<CostParameters> getCostParameters(UUID tenantId);

    /**
     * Computes the additional equipment operational cost for a FreelancerOrg mission.
     *
     * <p>Called by tnt-billing-pricing when building the total delivery price,
     * to add equipment costs (refrigeration, tracking, foam, etc.) to the base price.
     *
     * @param command the equipment cost computation command
     * @return equipment cost result with breakdown per type
     */
    Mono<EquipmentCostResult> computeEquipmentCost(ComputeEquipmentCostCommand command);

    /**
     * Retrieves the FleetCostParameters for a given FreelancerOrg or Agency.
     *
     * @param ownerOrgId the FreelancerOrg or Agency UUID
     * @return FleetCostParameters, or empty if not configured (falls back to global params)
     */
    Mono<FleetCostParameters> getFleetCostParameters(String ownerOrgId);

    /**
     * Persists or updates the FleetCostParameters for a given FreelancerOrg or Agency.
     *
     * @param params the fleet cost parameters to save
     * @return the saved FleetCostParameters
     */
    Mono<FleetCostParameters> saveFleetCostParameters(FleetCostParameters params);
}
