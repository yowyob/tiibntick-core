package com.yowyob.tiibntick.core.geo.application.port.in;

import com.yowyob.tiibntick.core.geo.domain.model.CostWeights;
import com.yowyob.tiibntick.core.geo.domain.model.RoadArcId;
import com.yowyob.tiibntick.core.geo.domain.model.WeatherCondition;
import reactor.core.publisher.Mono;

/**
 * Inbound port — compute the 5D composite cost of a road arc.
 *
 * omega(a,t) = alpha*d_tilde + beta*T_tilde + gamma*rho_tilde + delta*xi_tilde + eta*c_fuel_tilde
 *
 * This port is consumed by tnt-route-core's A* pathfinder to evaluate arc weights.
 *
 * Author: MANFOUO Braun
 */
public interface IComputeCostUseCase {

    /**
     * Computes the normalised composite cost for the given arc.
     * Fetches current weather conditions reactively from the weather adapter.
     *
     * @param arcId   the arc to evaluate
     * @param weights the alpha/beta/gamma/delta/eta weight set
     * @return a Mono emitting the composite cost in [0, infinity)
     */
    Mono<Double> computeCompositeCost(RoadArcId arcId, CostWeights weights);

    /**
     * Computes the cost using a pre-fetched weather condition (avoids extra HTTP call).
     * Used when batch-computing costs for many arcs under the same weather snapshot.
     */
    Mono<Double> computeCompositeCostWithWeather(RoadArcId arcId, CostWeights weights,
                                                  WeatherCondition weather);
}
