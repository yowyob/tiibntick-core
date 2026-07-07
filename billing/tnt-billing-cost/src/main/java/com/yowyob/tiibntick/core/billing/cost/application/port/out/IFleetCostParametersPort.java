package com.yowyob.tiibntick.core.billing.cost.application.port.out;

import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import reactor.core.publisher.Mono;

/**
 * Outbound port for {@link FleetCostParameters} persistence.
 *
 * <p>Implemented by FleetCostParametersAdapter (R2DBC).
 *
 * @author MANFOUO Braun
 */
public interface IFleetCostParametersPort {

    /**
     * Finds the FleetCostParameters for a given org.
     *
     * @param ownerOrgId the FreelancerOrg or Agency UUID
     * @return Mono containing the found parameters, or empty if not configured
     */
    Mono<FleetCostParameters> findByOwnerOrgId(String ownerOrgId);

    /**
     * Saves or updates the FleetCostParameters for an org (upsert).
     *
     * @param params the parameters to persist
     * @return the saved instance
     */
    Mono<FleetCostParameters> save(FleetCostParameters params);
}
