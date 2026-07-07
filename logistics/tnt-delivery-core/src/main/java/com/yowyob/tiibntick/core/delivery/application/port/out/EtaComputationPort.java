package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import reactor.core.publisher.Mono;

/**
 * Outbound port for ETA computation, delegating to the tnt-route-core module
 * which hosts the OR-Tools VRP solver and Kalman filter implementation.
 *
 * @author MANFOUO Braun
 */
public interface EtaComputationPort {

    /**
     * Computes an initial ETA estimate from current position to destination,
     * using the A* shortest path (Haversine heuristic) and log-normal travel model.
     *
     * @param origin      current position (driver location)
     * @param destination delivery address coordinates
     * @param distanceKm  pre-computed route distance
     * @return reactive ETA estimate
     */
    Mono<EtaEstimate> computeInitial(GeoCoordinates origin,
                                      GeoCoordinates destination,
                                      double distanceKm);

    /**
     * Refines an existing ETA using the extended Kalman filter based on real-time position.
     *
     * @param currentPosition latest GPS fix
     * @param destination     delivery address coordinates
     * @param previousEta     last known ETA
     * @return refined ETA estimate with increased confidence
     */
    Mono<EtaEstimate> refineWithKalman(GeoCoordinates currentPosition,
                                        GeoCoordinates destination,
                                        EtaEstimate previousEta);
}
